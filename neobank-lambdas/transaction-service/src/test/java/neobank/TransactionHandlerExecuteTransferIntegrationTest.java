package neobank;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sns.SnsClient;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises TransactionHandler.executeTransfer end-to-end (through the
 * public handleRequest entry point, since executeTransfer itself is
 * private) against a real Postgres instance: this method has zero coverage
 * elsewhere despite doing the actual money movement, including row-locking,
 * the Idempotency-Key check-then-act, and the Postgres 23505 race-loser
 * path - flagged in TESTING_NOTES.md as the single highest-value test
 * missing from the codebase.
 *
 * TransactionHandler resolves its DB pool from a `private static` (non
 * -final) HikariDataSource field, set once by a static block that reads
 * DB_URL/DB_USER/DB_PASSWORD from real environment variables at class-load
 * time. System.getenv() can't be overridden from inside an already-running
 * JVM, so instead this test reflectively overwrites that field to point at
 * the Testcontainers instance. Reflectively writing the field forces
 * TransactionHandler's class initialization to run at that point (JLS
 * 12.4.1), which also constructs the class's `snsClient` field via
 * SnsClient.create() - that call resolves an AWS region eagerly and throws
 * if none is configured, so it's done under a static mock here to keep the
 * test independent of whatever AWS credentials/region happen to be present
 * in the environment. SNS_TXN_TOPIC_ARN is never set in this test, so
 * publishToTopic() short-circuits before actually invoking the client.
 */
@Testcontainers
class TransactionHandlerExecuteTransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("neobank_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;

    private final TransactionHandler handler = new TransactionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String sender;
    private String recipient;
    private String senderCognitoSub;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        cognito_user_id VARCHAR(100) NOT NULL UNIQUE,
                        email VARCHAR(255) NOT NULL UNIQUE
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE accounts (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        account_number VARCHAR(18) NOT NULL UNIQUE,
                        balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                        available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_transaction_at TIMESTAMP
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE transaction_idempotency_keys (
                        idempotency_key VARCHAR(255) PRIMARY KEY,
                        transaction_id VARCHAR(100) NOT NULL,
                        from_account VARCHAR(18) NOT NULL,
                        to_account VARCHAR(18) NOT NULL,
                        amount DECIMAL(15,2) NOT NULL,
                        new_balance DECIMAL(15,2) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        try (MockedStatic<SnsClient> snsStatic = Mockito.mockStatic(SnsClient.class)) {
            snsStatic.when(SnsClient::create).thenReturn(Mockito.mock(SnsClient.class));

            Field field = TransactionHandler.class.getDeclaredField("dataSource");
            field.setAccessible(true);
            field.set(null, dataSource);
        }
    }

    @AfterAll
    static void tearDownDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void seedAccounts() throws Exception {
        senderCognitoSub = "cognito-" + UUID.randomUUID();
        sender = randomAccountNumber();
        recipient = randomAccountNumber();

        try (Connection conn = dataSource.getConnection()) {
            UUID senderUserId = UUID.randomUUID();
            UUID recipientUserId = UUID.randomUUID();

            insertUser(conn, senderUserId, senderCognitoSub);
            insertUser(conn, recipientUserId, "cognito-" + UUID.randomUUID());

            insertAccount(conn, senderUserId, sender, new BigDecimal("1000.00"));
            insertAccount(conn, recipientUserId, recipient, new BigDecimal("0.00"));
        }
    }

    @AfterEach
    void cleanUpRows() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM transaction_idempotency_keys");
            stmt.execute("DELETE FROM accounts");
            stmt.execute("DELETE FROM users");
        }
    }

    private static String randomAccountNumber() {
        return "MX" + Math.abs(UUID.randomUUID().hashCode());
    }

    private void insertUser(Connection conn, UUID id, String cognitoUserId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, cognito_user_id, email) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, cognitoUserId);
            ps.setString(3, cognitoUserId + "@test.com");
            ps.executeUpdate();
        }
    }

    private void insertAccount(Connection conn, UUID userId, String accountNumber, BigDecimal balance) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO accounts (id, user_id, account_number, balance, available_balance, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, accountNumber);
            ps.setBigDecimal(4, balance);
            ps.setBigDecimal(5, balance);
            ps.executeUpdate();
        }
    }

    private BigDecimal balanceOf(String accountNumber) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT balance FROM accounts WHERE account_number = ?")) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("balance");
            }
        }
    }

    private int idempotencyRowCount(String idempotencyKey) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM transaction_idempotency_keys WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private APIGatewayProxyRequestEvent request(String callerSub, String fromAccount, BigDecimal amount, String idempotencyKey) throws Exception {
        TransactionRequest body = new TransactionRequest();
        body.setFromAccount(fromAccount);
        body.setToAccount(recipient);
        body.setAmount(amount);
        body.setDescription("test transfer");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(objectMapper.writeValueAsString(body));

        if (idempotencyKey != null) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Idempotency-Key", idempotencyKey);
            event.setHeaders(headers);
        }

        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", callerSub);
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        event.setRequestContext(requestContext);

        return event;
    }

    private static Context testContext() {
        return new StubContext();
    }

    @Test
    void movesMoneyBetweenAccountsAndPersistsTheBalanceChange() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(senderCognitoSub, sender, new BigDecimal("100.00"), null), testContext());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(balanceOf(sender)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("100.00");
    }

    @Test
    void aRepeatedRequestWithTheSameIdempotencyKeyIsNotAppliedTwiceAndReturnsTheSameTransactionId() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();

        APIGatewayProxyResponseEvent first = handler.handleRequest(
                request(senderCognitoSub, sender, new BigDecimal("100.00"), idempotencyKey), testContext());
        APIGatewayProxyResponseEvent second = handler.handleRequest(
                request(senderCognitoSub, sender, new BigDecimal("100.00"), idempotencyKey), testContext());

        assertThat(first.getStatusCode()).isEqualTo(200);
        assertThat(second.getStatusCode()).isEqualTo(200);

        TransactionResponse firstBody = objectMapper.readValue(first.getBody(), TransactionResponse.class);
        TransactionResponse secondBody = objectMapper.readValue(second.getBody(), TransactionResponse.class);
        assertThat(secondBody.getTransactionId()).isEqualTo(firstBody.getTransactionId());

        assertThat(balanceOf(sender)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("100.00");
        assertThat(idempotencyRowCount(idempotencyKey)).isEqualTo(1);
    }

    @Test
    void twoConcurrentRequestsWithTheSameIdempotencyKeyOnlyMoveMoneyOnceAndReturnTheSameTransactionId() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);

        try {
            List<Future<APIGatewayProxyResponseEvent>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    bothReady.countDown();
                    bothReady.await(5, TimeUnit.SECONDS);
                    return handler.handleRequest(
                            request(senderCognitoSub, sender, new BigDecimal("100.00"), idempotencyKey), testContext());
                }));
            }

            List<TransactionResponse> bodies = new ArrayList<>();
            for (Future<APIGatewayProxyResponseEvent> future : futures) {
                APIGatewayProxyResponseEvent response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(200);
                bodies.add(objectMapper.readValue(response.getBody(), TransactionResponse.class));
            }

            assertThat(bodies.get(0).getTransactionId()).isEqualTo(bodies.get(1).getTransactionId());
            assertThat(balanceOf(sender)).isEqualByComparingTo("900.00");
            assertThat(balanceOf(recipient)).isEqualByComparingTo("100.00");
            assertThat(idempotencyRowCount(idempotencyKey)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentTransfersWithDifferentIdempotencyKeysBothApplyBecauseRowLockingSerializesThem() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);

        try {
            List<Future<APIGatewayProxyResponseEvent>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String key = "idem-" + UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    bothReady.countDown();
                    bothReady.await(5, TimeUnit.SECONDS);
                    return handler.handleRequest(
                            request(senderCognitoSub, sender, new BigDecimal("100.00"), key), testContext());
                }));
            }

            for (Future<APIGatewayProxyResponseEvent> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(200);
            }

            assertThat(balanceOf(sender)).isEqualByComparingTo("800.00");
            assertThat(balanceOf(recipient)).isEqualByComparingTo("200.00");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsATransferFromAnAccountThatDoesNotBelongToTheCaller() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("someone-else-cognito-sub", sender, new BigDecimal("100.00"), null), testContext());

        assertThat(response.getStatusCode()).isEqualTo(403);
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsATransferThatExceedsTheAvailableBalanceAndLeavesBalancesUnchanged() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request(senderCognitoSub, sender, new BigDecimal("5000.00"), null), testContext());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("INSUFFICIENT_FUNDS");
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsATransferToAnAccountThatDoesNotExist() throws Exception {
        TransactionRequest body = new TransactionRequest();
        body.setFromAccount(sender);
        body.setToAccount("MX-does-not-exist");
        body.setAmount(new BigDecimal("100.00"));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(objectMapper.writeValueAsString(body));
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", senderCognitoSub);
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        event.setRequestContext(requestContext);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, testContext());

        assertThat(response.getStatusCode()).isEqualTo(500);
        // The sender-side deduction happens before the recipient lookup fails,
        // but executeTransfer's catch block rolls the whole transaction back.
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
    }

    private static class StubContext implements Context {
        @Override
        public String getAwsRequestId() { return "test-request-id"; }

        @Override
        public String getLogGroupName() { return "test-log-group"; }

        @Override
        public String getLogStreamName() { return "test-log-stream"; }

        @Override
        public String getFunctionName() { return "transaction-service"; }

        @Override
        public String getFunctionVersion() { return "1"; }

        @Override
        public String getInvokedFunctionArn() { return "arn:aws:lambda:test:0:function:transaction-service"; }

        @Override
        public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }

        @Override
        public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }

        @Override
        public int getRemainingTimeInMillis() { return 30000; }

        @Override
        public int getMemoryLimitInMB() { return 512; }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) { }

                @Override
                public void log(byte[] message) { }
            };
        }
    }
}
