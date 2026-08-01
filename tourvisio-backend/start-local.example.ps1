# Bu dosyayi "start-local.ps1" olarak kopyalayin (start-local.ps1 .gitignore'da,
# kendi key'lerinizle commit'lenmez) ve asagidaki degerleri kendi key'lerinizle doldurun.
#
#   Copy-Item start-local.example.ps1 start-local.ps1
#
# Sonra:  .\start-local.ps1

# --- Gemini (ucretsiz) ---
$env:GEMINI_API_KEY="API_KEY_BURAYA"
$env:GEMINI_LITE_API_KEY="API_KEY_BURAYA"

# --- OpenRouter ---
$env:OPENROUTER_API_KEY="API_KEY_BURAYA"

# --- OpenAI (opsiyonel) ---
$env:AI_API_KEY=""

# --- Veritabani ---
$env:DB_URL="jdbc:postgresql://localhost:5432/tourvisio"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="YOUR_LOCAL_PASSWORD"

# --- TourVisio API ---
$env:TOURVISIO_AGENCY="INTERNSHIP"
$env:TOURVISIO_USERNAME="INTERNSHIP"
$env:TOURVISIO_PASSWORD="1"
$env:TOURVISIO_MOCK_MODE="false"

# --- Mail & Admin ---
$env:MAIL_USERNAME="sannydestek@gmail.com"
$env:MAIL_PASSWORD="YOUR_GMAIL_APP_PASSWORD"
$env:ADMIN_PASSWORD="YOUR_ADMIN_PASSWORD"
$env:ADMIN_EMAIL="sannydestek@gmail.com"
$env:GOOGLE_CLIENT_ID="YOUR_GOOGLE_CLIENT_ID"

.\mvnw spring-boot:run