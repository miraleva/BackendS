# Bu dosyayi "start-local.ps1" olarak kopyalayin (start-local.ps1 .gitignore'da,
# kendi key'lerinizle commit'lenmez) ve asagidaki degerleri kendi key'lerinizle doldurun.
#
#   Copy-Item start-local.example.ps1 start-local.ps1
#
# Sonra:  .\start-local.ps1

# --- Gemini (ucretsiz) ---
# Key almak icin: https://aistudio.google.com/apikey (Google hesabiyla, kredi karti gerekmez)
$env:GEMINI_API_KEY="BURAYA_GEMINI_KEY_YAZIN"
$env:GEMINI_LITE_API_KEY="BURAYA_GEMINI_KEY_YAZIN"

# --- OpenRouter (ucretsiz yedek modeller icin) ---
# Key almak icin: https://openrouter.ai -> hesap ac -> openrouter.ai/keys -> "Create Key"
# Odeme yontemi eklemenize gerek yok, ":free" etiketli modeller ucretsiz kullanilir.
$env:OPENROUTER_API_KEY="BURAYA_OPENROUTER_KEY_YAZIN"

# --- OpenAI (opsiyonel, su an kullanilmiyor ama alan mevcut) ---
$env:AI_API_KEY=""

# --- Veritabani ---
$env:DB_URL="jdbc:postgresql://localhost:5432/tourvisio"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="BURAYA_KENDI_POSTGRES_SIFRENIZI_YAZIN"

.\mvnw spring-boot:run
