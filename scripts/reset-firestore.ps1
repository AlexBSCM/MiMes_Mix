param(
    [switch]$Yes
)

$PROJECT_ID = "mimes-f9a2d"

# Проверка Firebase CLI
$firebase = Get-Command "firebase" -ErrorAction SilentlyContinue
if (-not $firebase) {
    Write-Host "Firebase CLI не найден." -ForegroundColor Red
    Write-Host "Установите: npm install -g firebase-tools" -ForegroundColor Yellow
    Write-Host "Затем выполните: firebase login" -ForegroundColor Yellow
    exit 1
}

# Проверка, залогинен ли пользователь
$loginCheck = firebase login:list 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Необходимо войти в Firebase CLI:" -ForegroundColor Yellow
    firebase login
}

# Подтверждение
if (-not $Yes) {
    Write-Host "ВНИМАНИЕ: Это удалит ВСЕ данные из коллекций Firestore!" -ForegroundColor Red
    $confirm = Read-Host "Продолжить? (y/N)"
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Write-Host "Отменено."
        exit 0
    }
}

Write-Host "Очищаю коллекцию chats..." -ForegroundColor Cyan
firebase firestore:delete --project $PROJECT_ID chats -y

if ($LASTEXITCODE -eq 0) {
    Write-Host "Готово. Коллекция chats удалена." -ForegroundColor Green
    Write-Host "Пользователь @AVZ будет создан автоматически при первом запуске приложения." -ForegroundColor Cyan
} else {
    Write-Host "Ошибка при удалении." -ForegroundColor Red
}
