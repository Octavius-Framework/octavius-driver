# Octavius JDBC - Architecture & Implementation Plan

Ten dokument opisuje założenia architektoniczne i plan implementacji dla rozbudowanej biblioteki `octavius-jdbc`, implementującej PostgreSQL Wire Protocol w języku Kotlin z naciskiem na natywną, binarną obsługę kompozytów i mapowanie wyników.

## 1. Główne Założenia (Core Principles)
- **Brak ORM**: Biblioteka nie jest ORMem. Wyniki zapytań SQL są domyślnie rzutowane na `Map<String, Any?>` lub obiekty DTO.
- **Typy z Bazy**: Typowanie danych w Kotlinie wynika na sztywno z typów w Postgresie.
- **Kompozyty Binarne**: Głównym powodem powstania projektu jest pierwszoklasowa, natywna obsługa binarna typów kompozytowych (traktowanych wewnętrznie tak samo jak wiersze/krotki).
- **Zgodność z JDBC**: Biblioteka implementuje minimalny zakres API `java.sql` (jako `Driver` / `Connection`), wystarczający do działania z pulami połączeń (głównie HikariCP). Zaawansowane funkcje biblioteki dostępne są poprzez odpakowanie (`unwrap(OctaviusConnection::class.java)`).

## 2. Decyzje Architektoniczne

### I/O i Bufory
- Zastosowanie **Standard Java Blocking I/O** (`java.net.Socket`, `InputStream`, `OutputStream`). Rozwiązanie proste, bez zewnetrznych zależności, docelowo świetnie działające z Java Virtual Threads.
- Do obróbki wiadomości i danych binarnych wykorzystujemy bezpośrednio buforowanie ze standardowej biblioteki (np. `ByteBuffer`) oraz samodzielnie napisane, lekkie wrappery zoptymalizowane pod C-style strings (null-terminated) i zerową alokację (zero-allocation) gdzie to możliwe.
- Brak zewnętrznych bibliotek (np. brak Netty, brak Okio).

### Modelowanie Protokołu Postgresa
- Odpowiedzi z serwera są parsowane na obiekty w architekturze **Sealed Classes** (`BackendMessage`, `FrontendMessage`).
- Protokół obsługuje **Maszyna Stanów** (State Machine), zarządzająca cyklem życia połączenia.

### Protokół Zapytań
- Od samego początku skupiamy się wyłącznie na **Extended Query Protocol** (`Parse` -> `Bind` -> `Execute` -> `Sync`).
- Tylko rozszerzony protokół pozwala na określenie, że chcemy wysyłać i odbierać dane w formacie czysto binarnym, co jest konieczne dla poprawnej i szybkiej obsługi kompozytów.

### Metadane (Type Registry) i Typy
- Kompozyt to na poziomie protokołu lista wartości (OID + Bajty).
- W czasie nawiązywania połączenia (lub leniwie w tle), biblioteka pobiera słowniki z tabel `pg_type`, `pg_attribute` oraz `pg_enum`.
- Mając w pamięci rejestr, potrafimy dynamicznie mapować odpowiedź binarną w postaci OID na konkretne nazwy kolumn kompozytu, uzyskując na wyjściu m.in. `Map<String, Any?>`.

### Autoryzacja i Inicjalizacja
- Projekt od startu implementuje algorytm **SCRAM-SHA-256**, który jest domyślnym standardem autoryzacji od PostgreSQL 10. Dzięki temu połączymy się z każdą domyślnie postawioną nowoczesną bazą z marszu.

## 3. Plan Implementacji (Pierwsze Kroki)

- [ ] **Krok 1: Inicjalizacja API JDBC**
  - Utworzenie klasy `OctaviusDriver` implementującej `java.sql.Driver`.
  - Rejestracja poprzez `META-INF/services/java.sql.Driver`.
  - Pusta klasa `OctaviusConnection` implementująca `java.sql.Connection`.
- [ ] **Krok 2: Warstwa Wejścia / Wyjścia (I/O)**
  - Utworzenie wrapperów m.in. `PgInputStream` (do odczytu tagów wiadomości 1-byte, długości Int32 oraz C-style stringów) na bazie `DataInputStream` / `ByteBuffer`.
  - Utworzenie `PgOutputStream`.
- [ ] **Krok 3: Wiadomości Fazowe (Sealed Classes)**
  - Zaprojektowanie hierarchii `BackendMessage` i `FrontendMessage`.
  - Stworzenie `StartupMessage` i implementacja pętli odczytu pierwszej odpowiedzi (`AuthenticationSASL` lub błędów).
- [ ] **Krok 4: SCRAM-SHA-256**
  - Zaimplementowanie mechanizmu challenge-response i kryptografii SASL / SCRAM-SHA-256, aby uzyskać status `AuthenticationOk`.
  - Oczekiwanie na komunikat `ReadyForQuery`.
- [ ] **Krok 5: Typy i Słowniki**
  - Stworzenie domyślnych dekoderów dla typów podstawowych (Int, String, itd.).
  - Pobranie i sparsowanie `pg_type` przy starcie.
- [ ] **Krok 6: Extended Protocol & Wyniki Binarne**
  - Oprogramowanie ramek `Parse`, `Bind`, `Execute`, `RowDescription` i `DataRow`.
  - Parsowanie binarnego formatu wyników na podstawie OID.
- [ ] **Krok 7: Kompozyty**
  - Obsługa rekursywnego (zagnieżdżonego) czytania wyników - krotka w krotce. Mapowanie do `Map` i docelowo do DTO.
