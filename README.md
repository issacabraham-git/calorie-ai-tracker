# 🥗 CalorieAI Tracker

An AI-powered Android calorie tracking app with a fully serverless AWS backend. Log meals using natural language or photos, track macros, and sync your data across devices — all powered by Gemini AI and AWS cloud infrastructure.

---

## 📱 Features

- **AI Food Logging** — Describe a meal in plain text and Gemini estimates calories, protein, carbs and fat instantly
- **Image Recognition** — Pick a photo from your gallery and the AI identifies the food and estimates nutrition
- **Manual Entry** — Add entries with custom nutrition values when you know the exact numbers
- **Daily Dashboard** — Visual calorie progress bar, macro breakdown, and remaining calories at a glance
- **Full History** — Browse and delete past entries grouped by date with daily totals
- **CSV Export** — Export your entire food log to a spreadsheet
- **Light / Dark Theme** — Persisted theme preference across sessions
- **User Authentication** — Secure email/password login via AWS Cognito with email verification
- **Cloud Sync** — All data stored in DynamoDB, fully restored after reinstall or device change

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Android App                         │
│         (Jetpack Compose + Kotlin)                      │
└─────────────────┬───────────────────────────────────────┘
                  │ HTTPS
                  ▼
┌─────────────────────────────────────────────────────────┐
│              AWS API Gateway (HTTP API)                 │
│         POST /calories  ←  single endpoint              │
└─────────────────┬───────────────────────────────────────┘
                  │ Trigger
                  ▼
┌─────────────────────────────────────────────────────────┐
│              AWS Lambda (Python 3.12)                   │
│                                                         │
│  Actions:                                               │
│  • analyze_food  → calls Gemini API                     │
│  • get_logs      → query DynamoDB                       │
│  • delete_entry  → delete from DynamoDB                 │
│  • save_profile  → write to UserProfiles table          │
│  • get_profile   → read from UserProfiles table         │
└──────┬──────────────────────────┬───────────────────────┘
       │                          │
       ▼                          ▼
┌─────────────┐         ┌─────────────────────┐
│  Gemini AI  │         │  AWS DynamoDB        │
│  (Google)   │         │                      │
│             │         │  CalorieLogs table   │
│  Food →     │         │  PK: userId          │
│  Nutrition  │         │  SK: id              │
│  estimates  │         │                      │
└─────────────┘         │  UserProfiles table  │
                        │  PK: userId          │
                        └─────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              AWS Cognito User Pool                      │
│                                                         │
│  • Email/password authentication                        │
│  • Email verification on signup                         │
│  • JWT token → userId (sub) used as DynamoDB key        │
└─────────────────────────────────────────────────────────┘
```
Click to view detailed system design:  
📄 [Architecture Document](./architecture.md)

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Mobile | Android (Kotlin, Jetpack Compose) |
| Auth | AWS Cognito User Pools |
| API | AWS API Gateway (HTTP API) |
| Backend | AWS Lambda (Python 3.12) |
| Database | AWS DynamoDB (On-demand) |
| AI | Google Gemini API (`gemini-flash-latest`) |
| Local Storage | SharedPreferences + Gson |

---

## ☁️ AWS Services Used

- **Amazon Cognito** — User pool with email verification, JWT-based auth
- **Amazon API Gateway** — Single HTTP API endpoint routing all requests to Lambda
- **AWS Lambda** — Serverless Python function handling all backend logic
- **Amazon DynamoDB** — NoSQL database with two tables (CalorieLogs, UserProfiles), on-demand capacity

---

## 🗂️ DynamoDB Schema

### CalorieLogs
| Attribute | Type | Description |
|---|---|---|
| userId | String (PK) | Cognito user sub |
| id | String (SK) | Timestamp-based unique ID |
| name | String | Food name |
| calories | String | Estimated calories |
| protein | String | Protein in grams |
| carbs | String | Carbs in grams |
| fat | String | Fat in grams |
| mealType | String | Breakfast / Lunch / Dinner / Snack |
| dateString | String | Date in YYYY-MM-DD format |

### UserProfiles
| Attribute | Type | Description |
|---|---|---|
| userId | String (PK) | Cognito user sub |
| weightKg | String | Body weight |
| heightCm | String | Height |
| age | String | Age |
| isMale | String | Biological sex |
| activityLevel | String | TDEE multiplier |
| goal | String | Lose / Maintain / Gain |
| dailyCalorieTarget | String | Calculated or manual target |

---

## 🚀 Setup & Running Locally

### Prerequisites
- Android Studio Hedgehog or later
- AWS account
- Google AI Studio API key

### 1. Clone the repo
```bash
git clone https://github.com/YOUR_USERNAME/calorie-ai-tracker.git
cd calorie-ai-tracker
```

### 2. Add your Gemini API key
Create or edit `local.properties` in the root of the project:
```
GEMINI_API_KEY=your_google_ai_studio_key_here
```

### 3. AWS Setup

**DynamoDB** — Create two tables:
- `CalorieLogs` — Partition key: `userId` (String), Sort key: `id` (String)
- `UserProfiles` — Partition key: `userId` (String)

**Lambda** — Create a Python 3.12 function, paste `lambda_function.py`, add environment variable `GEMINI_API_KEY`

**API Gateway** — Create an HTTP API, POST route `/calories` → Lambda integration

**Cognito** — Create a User Pool with email sign-in, create an App Client (no secret), enable `ALLOW_USER_PASSWORD_AUTH`

### 4. Update the API endpoint
In `MainActivity.kt`, update the constant:
```kotlin
const val API_URL = "https://YOUR_API_ID.execute-api.YOUR_REGION.amazonaws.com/calories"
```
And update Cognito constants:
```kotlin
const val COGNITO_REGION = "your-region"
const val COGNITO_CLIENT_ID = "your-client-id"
const val COGNITO_POOL_ID = "your-pool-id"
```

### 5. Build and run
Open in Android Studio → Run on device or emulator

---

## 💰 AWS Cost Estimate

At personal/demo usage levels this runs at effectively **$0/month**:

| Service | Free Tier | Typical Usage |
|---|---|---|
| Lambda | 1M requests/month | ~100 requests/day |
| API Gateway | 1M calls/month | ~100 calls/day |
| DynamoDB | 25GB + 25 RCU/WCU (permanent) | < 1MB data |
| Cognito | 50,000 MAU (permanent) | 1-5 users |

---

## 📁 Project Structure

```
app/
└── src/main/java/com/example/caloriecounter/
    └── MainActivity.kt        # Entire app — UI, auth, API calls, state

lambda/
    └── lambda_function.py     # AWS Lambda backend

```

---

## 🔒 Security Notes

- Gemini API key is stored server-side in Lambda environment variables — never exposed in the APK
- Cognito JWT tokens used for user identification
- API Gateway endpoint validates request structure before invoking Lambda

---

## 🗺️ Roadmap

- [ ] Validate Cognito access token on every Lambda request
- [ ] Implement token refresh logic (access tokens expire after 1 hour)
- [ ] Barcode scanner for packaged foods (Open Food Facts API)
- [ ] Weekly calorie trend chart
- [ ] Push notification reminders
- [ ] Migrate local storage from SharedPreferences to Room database

---

## 👤 Author

Built by Issac — AI/ML Graduate | AWS Solutions Architect Associate

---

## 📄 License

MIT License
