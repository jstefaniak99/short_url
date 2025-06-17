# System Skracania URL - Dokumentacja

## Spis treści
1. [Architektura systemu](#architektura-systemu)
2. [Komponenty systemu](#komponenty-systemu)
3. [Funkcjonalności](#funkcjonalności)
4. [Kluczowe fragmenty kodu](#kluczowe-fragmenty-kodu)
5. [Instrukcja uruchomienia i testowania](#instrukcja-uruchomienia-i-testowania)

---

## Architektura systemu

System składa się z trzech mikroserwisów działających w kontenerach Docker:

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│  Shortener Service  │     │  Redirect Service   │     │  Cleanup Service    │
│      (port 8081)    │     │     (port 8082)     │     │     (port 8083)     │
└──────────┬──────────┘     └──────────┬──────────┘     └──────────┬──────────┘
           │                             │                             │
           └─────────────────────────────┴─────────────────────────────┘
                                         │
                              ┌──────────▼──────────┐
                              │     Cassandra       │
                              │   (port 9042)       │
                              └──────────┬──────────┘
                                         │
                              ┌──────────▼──────────┐
                              │       Kafka         │
                              │   (port 9092)       │
                              └─────────────────────┘
```

### Wykorzystane technologie:
- **Spring Boot 3.1.0** - framework aplikacji
- **Apache Cassandra 3.11.8** - baza danych NoSQL
- **Apache Kafka** - system kolejkowania wiadomości
- **Docker & Docker Compose** - konteneryzacja
- **Java 17** - język programowania

---

## Komponenty systemu

### 1. **Shortener Service** (port 8081)
Odpowiada za:
- Przyjmowanie żądań skracania URL
- Generowanie skrótów metodą Base62
- Sprawdzanie słów zakazanych
- Wysyłanie alertów do Kafki

### 2. **Redirect Service** (port 8082)
Odpowiada za:
- Przekierowanie ze skróconego URL na oryginalny
- Aktualizację czasu ostatniego dostępu
- Walidację czasu ważności linku

### 3. **Cleanup Service** (port 8083)
Odpowiada za:
- Automatyczne usuwanie wygasłych linków
- Czyszczenie według strategii (czas utworzenia lub ostatniego dostępu)
- Raportowanie statystyk czyszczenia

---

## Funkcjonalności

### 1. Skracanie URL

**Endpoint:** `POST http://localhost:8081/shorten`

**Przykład żądania:**
```bash
curl -X POST http://localhost:8081/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.example.com"}'
```

**Proces skracania:**
1. Walidacja URL (czy nie jest pusty)
2. Sprawdzenie słów zakazanych
3. Generowanie skrótu Base62 z MD5
4. Zapis do Cassandry z TTL
5. Zwrócenie skróconego URL

### 2. Przekierowanie

**Endpoint:** `GET http://localhost:8082/{shortKey}`

**Przykład:**
```bash
curl -v http://localhost:8082/ABC123xyz
```

**Proces przekierowania:**
1. Wyszukanie w bazie po kluczu
2. Sprawdzenie czy link nie wygasł
3. Aktualizacja czasu ostatniego dostępu
4. Zwrócenie przekierowania 302

### 3. Filtrowanie słów zakazanych

System automatycznie blokuje URL-e zawierające słowa zakazane (np. "spam", "phishing", "malware", "onet").

**Zarządzanie słowami zakazanymi:**
```bash
# Pobranie listy
GET http://localhost:8081/admin/forbidden-words

# Dodanie nowego słowa
POST http://localhost:8081/admin/forbidden-words
{"word": "badword"}

# Usunięcie słowa
DELETE http://localhost:8081/admin/forbidden-words/badword
```

### 4. Automatyczne czyszczenie

Scheduler uruchamiany co minutę (konfigurowalny) usuwa:
- URL-e starsze niż 3 minuty (strategia CREATION_TIME)
- URL-e nieużywane od 3 minut (strategia LAST_ACCESS_TIME)

---

## Kluczowe fragmenty kodu

### 1. Generowanie skrótu Base62

```java
private String generateBase62Hash(String originalUrl) {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] digest = md.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
    long value = 0;
    for (int i = 0; i < 6; i++) {
        value = (value << 8) | (digest[i] & 0xFF);
    }
    return toBase62(value);
}
```
*(Używamy MD5 dla szybkości, bierzemy pierwsze 6 bajtów dla unikalności, konwertujemy na Base62 dla krótkich, czytelnych URL-i)*

### 2. Model danych w Cassandrze

```java
@Table("short_url_entity")
public class ShortUrlEntity {
    @PrimaryKey("short_key")
    private String shortKey;
    
    @Column("original_url")
    private String originalUrl;
    
    @Column("expiration_time")
    private long expirationTime;
    
    @Column("creation_time")
    private long creationTime;
    
    @Column("last_access_time")
    private long lastAccessTime;
}
```
*(Używamy timestamp jako long dla łatwych porównań, klucz główny to shortKey dla szybkiego wyszukiwania)*

### 3. Sprawdzanie słów zakazanych z alertem Kafka

```java
public String shortenUrl(String originalUrl) {
    // Sprawdzenie słów zakazanych
    Optional<String> forbiddenWord = checkForForbiddenWords(originalUrl);
    if (forbiddenWord.isPresent()) {
        // Wysłanie alertu na Kafkę
        sendForbiddenWordAlert(originalUrl, forbiddenWord.get());
        throw new IllegalArgumentException("URL zawiera zabronione słowo");
    }
    // ... reszta logiki
}
```
*(Asynchroniczne powiadomienie przez Kafkę nie blokuje odpowiedzi, ale pozwala na monitoring bezpieczeństwa)*

### 4. Strategia czyszczenia

```java
if (strategy == CleanupStrategy.CREATION_TIME) {
    long cutoff = now - TimeUnit.MINUTES.toMillis(maxAgeMinutes);
    toDelete = repo.findAllWithCreationTimeBefore(cutoff);
} else {
    long cutoff = now - TimeUnit.MINUTES.toMillis(inactiveMinutes);
    toDelete = repo.findAllWithLastAccessTimeBefore(cutoff);
}
```
*(ALLOW FILTERING w Cassandrze jest użyte świadomie - przy małej ilości danych jest akceptowalne)*

### 5. Listener Kafki dla blokowanych URL-i

```java
@KafkaListener(topics = "blocked-urls", groupId = "url-shortener-group")
public void handleBlockedUrlMessage(String message) {
    if (message.toUpperCase().contains("BLOCKED")) {
        String url = extractUrl(message);
        if (url != null) {
            blockedUrls.add(url);
            logger.warn("⚠️  URL ZABLOKOWANY: {}", url);
        }
    }
}
```
*(ConcurrentHashMap.newKeySet() zapewnia thread-safety dla współbieżnych operacji)*

---

## Instrukcja uruchomienia i testowania

### Krok 1: Uruchomienie systemu

```bash
# W głównym katalogu projektu
docker-compose up --build
```

Poczekaj aż wszystkie serwisy się uruchomią (około 30-60 sekund).

### Krok 2: Testowanie skracania URL

```bash
# Skróć normalny URL
curl -X POST http://localhost:8081/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.wp.pl"}'

# Odpowiedź: {"shortUrl":"http://localhost:8081/3fKbS8A"}

# Spróbuj skrócić URL z zakazanym słowem
curl -X POST http://localhost:8081/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.onet.pl"}'

# Odpowiedź: {"error":"URL zawiera zabronione słowo: onet"}
```

### Krok 3: Testowanie przekierowania

```bash
# Użyj skrótu z kroku 2
curl -v http://localhost:8082/3fKbS8A

# Zobaczysz w odpowiedzi:
# < HTTP/1.1 302
# < Location: https://www.wp.pl
```

### Krok 4: Sprawdzenie danych w Cassandrze

```bash
# Połącz się z Cassandrą
docker exec -it cassandra cqlsh

# W konsoli CQL:
USE redirect_keyspace;
SELECT * FROM short_url_entity;

# Zobaczysz wszystkie zapisane URL-e z timestampami
```

### Krok 5: Testowanie automatycznego czyszczenia

```bash
# Utwórz kilka URL-i
for i in {1..5}; do
  curl -X POST http://localhost:8081/shorten \
    -H "Content-Type: application/json" \
    -d "{\"url\":\"https://example$i.com\"}"
done

# Sprawdź liczbę rekordów
docker exec -it cassandra cqlsh -e \
  "SELECT COUNT(*) FROM redirect_keyspace.short_url_entity;"

# Poczekaj 3-4 minuty

# Sprawdź ponownie - powinno być 0 rekordów
docker exec -it cassandra cqlsh -e \
  "SELECT COUNT(*) FROM redirect_keyspace.short_url_entity;"
```

### Krok 6: Sprawdzenie logów czyszczenia

```bash
# Zobacz logi cleanup service
docker logs cleanup_service | grep "DB shrank"

# Przykładowy log:
# DB shrank by 5 rows in last cleanup (5 → 0)
```

### Krok 7: Testowanie Kafki (słowa zakazane)

```bash
# Sprawdź logi shortenera przy próbie dodania zakazanego URL
docker logs shortener-service | grep "Forbidden word"

# Możesz też sprawdzić topiki Kafki
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Przydatne komendy debugowania

```bash
# Status kontenerów
docker ps

# Logi poszczególnych serwisów
docker logs shortener-service
docker logs redirect-service
docker logs cleanup-service

# Restart pojedynczego serwisu
docker-compose restart shortener-service

# Zatrzymanie i wyczyszczenie
docker-compose down -v
```

---

## Podsumowanie

System realizuje wszystkie wymagane funkcjonalności:
- ✅ Skracanie URL z generowaniem unikalnych kluczy
- ✅ Przekierowanie na oryginalne URL
- ✅ Przechowywanie danych w Cassandrze
- ✅ Automatyczne czyszczenie wygasłych linków
- ✅ Filtrowanie słów zakazanych
- ✅ Powiadomienia przez Kafkę

System jest gotowy do prezentacji i demonstracji wszystkich funkcjonalności.