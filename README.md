# System Skracania URL-i - Dokumentacja

## Spis treści
1. [Architektura systemu](#architektura-systemu)
2. [Komponenty](#komponenty)
3. [Funkcjonalności](#funkcjonalności)
4. [Kluczowe fragmenty kodu](#kluczowe-fragmenty-kodu)
5. [Instrukcja uruchomienia](#instrukcja-uruchomienia)
6. [Testowanie funkcjonalności](#testowanie-funkcjonalności)

---

## Architektura systemu

System składa się z trzech mikroserwisów:
- **Shortener Service** (port 8081) - skracanie URL-i
- **Redirect Service** (port 8082) - przekierowania
- **Cleanup Service** (port 8083) - automatyczne czyszczenie

### Technologie:
- **Spring Boot 3.1.0** z Java 17
- **Apache Cassandra** - baza NoSQL
- **Apache Kafka** - komunikacja asynchroniczna
- **Docker Compose** - orkiestracja kontenerów

---

## Komponenty

### 1. Shortener Service (port 8081)
Odpowiada za:
- Generowanie skróconych URL-i
- Weryfikację słów zakazanych
- Wysyłanie alertów do Kafki
- Zapis do Cassandry

### 2. Redirect Service (port 8082)
Odpowiada za:
- Obsługę przekierowań
- Aktualizację czasu ostatniego dostępu
- Weryfikację ważności linków

### 3. Cleanup Service (port 8083)
Odpowiada za:
- Cykliczne usuwanie starych wpisów
- Dwie strategie czyszczenia (CREATION_TIME / LAST_ACCESS_TIME)
- Raportowanie statystyk

---

## Funkcjonalności

### 1. Skracanie URL-i

**Endpoint:** `POST http://localhost:8081/shorten`

Proces skracania:
1. Weryfikacja słów zakazanych
2. Generowanie klucza Base62 z MD5
3. Zapis do Cassandry z TTL (domyślnie 3 minuty)
4. Zwrócenie skróconego URL-a

### 2. Przekierowanie

**Endpoint:** `GET http://localhost:8082/{shortKey}`

Proces przekierowania:
1. Pobranie wpisu z bazy
2. Weryfikacja ważności (TTL)
3. Aktualizacja czasu ostatniego dostępu
4. Zwrócenie przekierowania 302

### 3. Automatyczne czyszczenie

Scheduler uruchamiany co minutę (konfigurowalny):
- Usuwa wpisy starsze niż 3 minuty
- Loguje statystyki czyszczenia

### 4. Filtrowanie słów zakazanych

Lista słów zakazanych:
- spam, phishing, malware, onet, virus, scam, hack

Przy wykryciu:
- Odrzucenie żądania
- Wysłanie alertu do Kafki (topic: `forbidden-words-topic`)

---

## Kluczowe fragmenty kodu

### 1. Generowanie skróconego klucza (ShortUrlService.java)

```java
private String generateBase62Hash(String originalUrl) {
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
        long value = 0;
        for (int i = 0; i < 6; i++) {
            value = (value << 8) | (digest[i] & 0xFF);
        }
        return toBase62(value);
    } catch (Exception e) {
        throw new RuntimeException("Error generating hash", e);
    }
}
```
*(Używamy MD5 do generowania unikalnego hasza, a następnie konwertujemy pierwsze 6 bajtów na Base62 - zapewnia to krótkie, unikalne identyfikatory)*

### 2. Model danych Cassandra (ShortUrlEntity.java)

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
*(Używamy timestampów zamiast dat, co jest bardziej efektywne w Cassandrze. short_key jako klucz główny zapewnia szybkie wyszukiwanie)*

### 3. Weryfikacja słów zakazanych (ForbiddenWordService.java)

```java
public Optional<String> checkForForbiddenWords(String url) {
    String urlLowerCase = url.toLowerCase();
    return forbiddenWords.stream()
            .filter(forbiddenWord -> urlLowerCase.contains(forbiddenWord.toLowerCase()))
            .findFirst();
}
```
*(Stream API pozwala na eleganckie sprawdzenie wszystkich zakazanych słów. toLowerCase() zapewnia niezależność od wielkości liter)*

### 4. Scheduler czyszczenia (CleanupScheduler.java)

```java
@Scheduled(cron = "${cleanup.schedule:0 * * * * ?}")
public void scheduledCleanup() {
    if (!cleanupEnabled) {
        logger.info("Cleanup is disabled, skipping scheduled execution");
        return;
    }
    logger.info("Starting scheduled cleanup task");
    int deletedCount = cleanupService.cleanupOldUrls();
}
```
*(Cron expression "0 * * * * ?" oznacza uruchomienie co pełną minutę. Konfigurowalny przez properties)*

### 5. Obsługa przekierowania (RedirectController.java)

```java
@GetMapping("/{shortKey}")
public ResponseEntity<?> redirect(@PathVariable String shortKey) {
    String originalUrl = redirectService.getOriginalUrl(shortKey);
    if (originalUrl == null) {
        return ResponseEntity.notFound().build();
    }
    HttpHeaders headers = new HttpHeaders();
    headers.add("Location", originalUrl);
    return ResponseEntity.status(302).headers(headers).build();
}
```
*(Status 302 to tymczasowe przekierowanie - właściwe dla dynamicznych linków, które mogą wygasnąć)*

---

## Instrukcja uruchomienia

### Krok 1: Zbudowanie i uruchomienie kontenerów

```bash
# W głównym katalogu projektu
docker-compose up --build
```

Poczekaj aż wszystkie serwisy się uruchomią. Zobaczysz logi:
- Cassandra initialization
- Kafka startup
- Spring Boot services startup

### Krok 2: Weryfikacja działania

Sprawdź czy serwisy są dostępne:
- http://localhost:8081 (Shortener)
- http://localhost:8082 (Redirect)
- http://localhost:8083 (Cleanup)

---

## Testowanie funkcjonalności

### 1. Skracanie URL-a

```bash
# Skróć zwykły URL
curl -X POST http://localhost:8081/shorten -H "Content-Type: application/json" -d "{\"url\":\"https://www.wp.pl\"}"

# Odpowiedź:
{"shortUrl":"http://localhost:8081/AbCdEf"}
```

### 2. Testowanie słów zakazanych

```bash
# Próba skrócenia URL-a z zakazanym słowem
curl -X POST http://localhost:8081/shorten -H "Content-Type: application/json" -d "{\"url\":\"https://www.onet.pl\"}"

# Odpowiedź:
{"error":"URL zawiera zakazane słowo: 'onet' - https://www.onet.pl"}
```

### 3. Przekierowanie

```bash
# Użyj klucza otrzymanego w kroku 1
curl -v http://localhost:8082/AbCdEf

# Zobaczysz:
< HTTP/1.1 302
< Location: https://www.wp.pl
```

### 4. Sprawdzenie alertów Kafka

```bash
# Sprawdź alerty o zakazanych słowach
docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic forbidden-words-topic --from-beginning
```

### 5. Podgląd danych w Cassandrze

```bash
# Połącz się z Cassandrą
docker exec -it cassandra cqlsh

# Wykonaj zapytania
USE redirect_keyspace;
SELECT * FROM short_url_entity;

# Zobaczysz kolumny:
# short_key | original_url | creation_time | expiration_time | last_access_time
```

### 6. Obserwacja automatycznego czyszczenia

```bash
# Obserwuj logi cleanup service
docker logs -f short_url-cleanup-service-1
JEŻELI JEST PROBLEM ZOBACZ CZY NIE MA INNEJ NAZWY USŁUGA !!! -.-

# Co minutę zobaczysz:
# "DB shrank by 3 rows in last cleanup (3 → 0)"
Czyli można to czytać, że 3 wpisy zostały usunięte i teraz nie ma żadnego rekordu w tabeli
```

### 7. Test wygasania linków

1. Stwórz link (TTL = 3 minuty)
2. Użyj go zaraz po utworzeniu - działa
3. Poczekaj 3+ minuty
4. Spróbuj ponownie - otrzymasz 404

---

## Wskazówki do prezentacji

### Kolejność demonstracji:

1. **Start systemu** - pokaż docker-compose up
2. **Skrócenie URL-a** - pokaż curl POST
3. **Przekierowanie** - otwórz w przeglądarce
4. **Cassandra** - pokaż dane w bazie
5. **Słowa zakazane** - pokaż odrzucenie i alert Kafka
6. **Automatyczne czyszczenie** - pokaż logi po 3 minutach

### Punkty do podkreślenia:

- **Mikroserwisy** - niezależne deployowanie i skalowanie
- **NoSQL (Cassandra)** - wydajność dla prostych zapytań po kluczu
- **Kafka** - asynchroniczna komunikacja, audit trail
- **TTL** - automatyczne wygasanie bez dodatkowej logiki
- **Base62** - krótkie, czytelne identyfikatory

### Potencjalne pytania:

**Q: Dlaczego Cassandra zamiast Redis?**
A: Trwałość danych, replikacja, lepsze dla większej skali

**Q: Dlaczego MD5 do hashowania?**
A: Wystarczające dla generowania ID, nie używamy do bezpieczeństwa

**Q: Co jeśli kolizja hash?**
A: Bardzo mało prawdopodobne, ale można dodać retry z saltem

---

## Konfiguracja

### Zmiana czasu życia linków:
```properties
# shorturl2/src/main/resources/application.properties
short.url.ttl.seconds=180  # zmień na dowolną wartość
```

### Zmiana strategii czyszczenia:
```properties
# cleanup_service/src/main/resources/application.properties
cleanup.strategy=LAST_ACCESS_TIME  # lub CREATION_TIME
cleanup.max-age=3  # minuty
```

### Zmiana harmonogramu czyszczenia:
```properties
cleanup.schedule=0 0 1 * * ?  # codziennie o 1:00
```
