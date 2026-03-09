// ==========================================
// NEOBANK TYPES
// ==========================================

// ── Auth ──────────────────────────────────
export interface SignupRequest {
  email: string;
  password: string;
  fullName: string;
  phone: string;
  dateOfBirth?: string;
  curp?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  success: boolean;
  message: string;
  data: {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    user: User;
  };
}

export interface SignupResponse {
  success: boolean;
  message: string;
  data: {
    message: string;
    userId: string;
    accountNumber: string;
    status: string;
  };
}

// ── User ──────────────────────────────────
export interface User {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  dateOfBirth?: string;
  curp?: string;
  street?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  kycStatus: "PENDING" | "IN_PROGRESS" | "APPROVED" | "REJECTED";
  createdAt?: string;
}

export interface UserSettings {
  emailNotifications: boolean;
  pushNotifications: boolean;
  smsNotifications: boolean;
  mfaEnabled: boolean;
  biometricEnabled: boolean;
  language: string;
  currency: string;
  theme: string;
}

// ── Account ───────────────────────────────
export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: "CHECKING" | "SAVINGS";
  balance: number;
  availableBalance: number;
  currency: string;
  status: "ACTIVE" | "FROZEN" | "CLOSED";
  overdraftLimit?: number;
  interestRate?: number;
  lastTransactionAt?: string;
  createdAt?: string;
}

export interface AccountBalance {
  accountId: string;
  balance: number;
  availableBalance: number;
  currency: string;
  lastUpdated: string;
}

// ── Transactions ──────────────────────────
export interface Transaction {
  id: string;
  transactionId?: string;
  fromAccount: string;
  toAccount: string;
  amount: number;
  currency: string;
  status: "COMPLETED" | "PENDING" | "FAILED" | "CANCELLED";
  type: "TRANSFER" | "DEPOSIT" | "WITHDRAWAL" | "PAYMENT";
  description?: string;
  reference?: string;
  newBalance?: number;
  createdAt: string;
  timestamp?: number;
}

export interface TransferRequest {
  from_account: string;
  to_account: string;
  amount: number;
  currency?: string;
  description?: string;
  reference?: string;
}

export interface TransactionHistory {
  transactions: Transaction[];
  total: number;
  page: number;
  limit: number;
}

// ── KYC ───────────────────────────────────
export type DocumentType = "INE_FRONT" | "INE_BACK" | "PASSPORT" | "SELFIE" | "PROOF_OF_ADDRESS";

export interface KycStatus {
  userId: string;
  overallStatus: "PENDING" | "IN_PROGRESS" | "APPROVED" | "REJECTED";
  documents: KycDocument[];
  verifiedAt?: string;
}

export interface KycDocument {
  id: string;
  documentType: DocumentType;
  status: "PENDING" | "APPROVED" | "REJECTED";
  aiConfidence?: number;
  rejectionReason?: string;
  createdAt: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  s3Key: string;
  expiresIn: number;
}

// ── Notifications ─────────────────────────
export interface Notification {
  id: string;
  type: "TRANSACTION" | "SECURITY" | "KYC" | "SYSTEM";
  title: string;
  message: string;
  read: boolean;
  data?: Record<string, unknown>;
  createdAt: string;
}

// ── Security ──────────────────────────────
export interface UserSession {
  id: string;
  device: string;
  location: string;
  ipAddress: string;
  lastActive: string;
}

export interface FraudAlert {
  id: string;
  type: "SUSPICIOUS_TRANSACTION" | "UNUSUAL_LOGIN" | "MULTIPLE_FAILED";
  severity: "LOW" | "MEDIUM" | "HIGH";
  message: string;
  transactionId?: string;
  status: "PENDING_REVIEW" | "CONFIRMED" | "DISMISSED";
  createdAt: string;
}

// ── Contacts ──────────────────────────────
export interface Contact {
  id: string;
  name?: string;
  accountNumber: string;
  nickname?: string;
  favorite: boolean;
  createdAt: string;
}

// ── Analytics ─────────────────────────────
export interface SpendingAnalytics {
  period: string;
  totalSpent: number;
  totalReceived: number;
  categories: SpendingCategory[];
  monthlyData: MonthlyData[];
}

export interface SpendingCategory {
  name: string;
  amount: number;
  percentage: number;
  color: string;
}

export interface MonthlyData {
  month: string;
  spent: number;
  received: number;
}

// ── Support ───────────────────────────────
export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
}

export interface SupportTicket {
  id: string;
  subject: string;
  description: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  priority: "LOW" | "MEDIUM" | "HIGH";
  createdAt: string;
}

// ── API Responses ─────────────────────────
export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  data: T;
}

export interface ApiError {
  success: false;
  message: string;
  errors?: Record<string, string[]>;
  code?: string;
}
