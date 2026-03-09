# 🏦 NeoBank Frontend

Next.js 15 + React 19 + Tailwind CSS v4 frontend for the NeoBank application.

## 🚀 Quick Start

```bash
npm install
npm run dev
```

## ⚙️ Environment Variables

Copy `.env.local` and fill in your values:

```bash
# EC2 Spring Boot Backend
NEXT_PUBLIC_API_URL=http://YOUR_EC2_IP:8080

# AWS API Gateway (for Lambda endpoints)
NEXT_PUBLIC_LAMBDA_URL=https://YOUR_API_GATEWAY_ID.execute-api.us-east-1.amazonaws.com/prod
```

## 📁 Project Structure

```
app/
  auth/          → Login, Signup, Verify Email, Forgot/Reset Password
  dashboard/     → Main dashboard with balance + recent transactions
  accounts/      → Account management, freeze/unfreeze
  transactions/  → Full transaction history with filters
  transfer/      → SPEI transfer flow (3-step)
  kyc/           → Document upload & verification status
  notifications/ → Push notification center
  security/      → Sessions, MFA, Fraud alerts
  contacts/      → Saved recipients
  analytics/     → Spending charts (Recharts)
  support/       → AI Chatbot (Lex fulfillment Lambda)
  settings/      → Profile, preferences, password change

lib/
  api/           → All API modules (auth, accounts, transactions, etc.)
  store/         → Zustand auth store
  utils.ts       → Helpers (formatMXN, formatCLABE, etc.)

components/
  ui/            → Button, Input, Card, Badge, Modal
  layout/        → Sidebar, Header
```

## 🔗 Backend Endpoints Covered

| Module | Type | Endpoints |
|--------|------|-----------|
| Auth | Spring Boot | signup, login, verify-email, refresh-token, logout, forgot-password, reset-password, change-password |
| Users | Spring Boot | profile GET/PUT, settings GET/PUT |
| Accounts | Spring Boot | list, details, balance, statement, freeze, unfreeze |
| Transactions | Lambda | transfer, validate-recipient, history, get by ID, receipt, scheduled |
| KYC | Spring Boot | upload-url, verify, status |
| Notifications | Lambda | list, mark-read, register-device |
| Security | Lambda + Spring Boot | sessions, terminate session, MFA enable/verify/disable, fraud alerts |
| Contacts | Spring Boot | list, add, remove, toggle-favorite |
| Analytics | Lambda | spending analytics |
| Support | Lambda (Lex) | chat, FAQ, tickets |
