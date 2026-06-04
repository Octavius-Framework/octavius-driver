# 🗃️ Formatowanie typów w PostgreSQL

PostgreSQL udostępnia cztery kluczowe funkcje obsługujące konwersję typów pomiędzy różnymi formatami. Warto zwrócić uwagę na ich wymagalność:

- `send` – konwersja na format binarny (nieobowiązkowa)
- `receive` – konwersja z formatu binarnego (nieobowiązkowa)
- **`input`** – konwersja na reprezentację tekstową (**wymagana**)
- **`output`** – konwersja z reprezentacji tekstowej (**wymagana**)

> [!NOTE]
> Funkcje `send` i `receive` mogą nie istnieć dla niektórych typów (szczególnie niestandardowych). Wtedy typ obsługiwany jest wyłącznie tekstowo. Wewnątrz tabeli `pg_type` dla takich typów `typsend == 0`.

## 📦 Uwagi dotyczące protokołu binarnego

1. **Typy niestandardowe:** Jeżeli typ niestandardowy nie posiada funkcji `send` i `receive`, przesył w formacie binarnym jest niemożliwy.
2. **Kolekcje (kompozyty, tablice, zakresy, multirange):** Jeśli choć jedno pole wewnątrz kolekcji nie posiada binarnej funkcji przesyłu, przy próbie przesyłu jako postać binarna wystąpi błąd.
3. **Domeny:** Zachowują swoje OID wewnątrz kontenerów (np. w kompozytach lub tablicach). Na głównym poziomie OID domeny nigdy nie jest widoczne (zastępuje je OID typu bazowego).

### 🛠️ Struktura binarnego przesyłu

* **Kompozyty:** 
  `liczba kolumn + (OID + długość + dane binarne) * liczba kolumn`
* **Tekst:** 
  Zapisany binarnie z wykorzystaniem kodowania ustawionego w bazie danych.

---

# 📝 Plan wdrożenia: Rejestr Typów (Type Registry)

Rejestr służy do śledzenia mapowań typów pomiędzy PostgreSQL a sterownikiem JDBC oraz określania odpowiedniego sposobu ich przesyłu.

## 1. Co przechowujemy w Rejestrze?

Każdy typ zarejestrowany w systemie powinien zawierać następujące informacje:

* **OID** – unikalny identyfikator typu w bazie.
* **Nazwa typu** – oryginalna nazwa z PostgreSQL.
* **Schemat** – przestrzeń nazw (schema), w której typ jest zdefiniowany.
* **Format przesyłu** – określany na podstawie dostępności binarnej obsługi typu (według powyższych reguł):
  * `Format 0` – Tekstowo
  * `Format 1` – Binarnie

### Specyficzne metadane dla poszczególnych rodzajów typów:

| Rodzaj typu             | Wymagane dodatkowe metadane                               |
|:------------------------|:----------------------------------------------------------|
| **Typy bazowe (Base)**  | Funkcje do odczytu z formatu binarnego oraz tekstowego.   |
| **Tablice (Arrays)**    | OID typu elementu bazowego (`typelem` w `pg_type`).       |
| **Zakresy (Ranges)**    | OID podtypu (`subtype`) – np. OID `int4` dla `int4range`. |
| **Kompozyty (Records)** | OID-y atrybutów oraz ich nazwy w odpowiedniej kolejności. |
| **Domeny (Domains)**    | OID typu bazowego (`typbasetype`).                        |
| **Słowniki (Enums)**    | Przypisana wartość i/lub jej pozycja.                     |

## 2. Kiedy aktualizować rejestr?

- **Podczas startu:** Inicjalne wczytanie informacji o typach dla zoptymalizowanego działania.
- **Na żądanie (jawnie):** Przez dedykowaną metodę `reloadTypes()`, która odświeży rejestr, np. po utworzeniu nowych typów w bazie podczas działania aplikacji.

## Struktura rejestru dla typów podstawowych
```kotlin
    interface TypeHandler<T : Any> {                                                                                                                                                                                                                                                                           
        val pgTypeName: String                                                                                                                                                                                                                                                                                 
        val pgSchema: String get() = ""                                                                                                                                                                                                                                                                        
        val kotlinClass: KClass<T>                                                                                                                                                                                                                                                                             
        val isDefaultForKotlinType: Boolean get() = false                                                                                                                                                                                                                                                      
                                                                                                                                                                                                                                                                                                               
        // Zoptymalizowana ścieżka główna:                                                                                                                                                                                                                                                                     
        val fromBinary: ((PgBuffer) -> T)? get() = null                                                                                                                                                                                                                                                        
        val toBinary: ((T, PgBuffer) -> Unit)? get() = null                                                                                                                                                                                                                                                    
                                                                                                                                                                                                                                                                                                               
        // Wygodny fallback tekstowy:                                                                                                                                                                                                                                                                          
        val fromPgString: (String) -> T                                                                                                                                                                                                                                                                        
        val toPgString: (T) -> String                                                                                                                                                                                                                                                                          
    }                       
```

Fallback tekstowy będzie tylko dla nowych "base type" które były dodane z C, reszta ma funkcje send i receive

# Row a Mapa/Klasa

Row MUSI mieć metody do zmiany go w klasę (refleksją lub jawnym mapperem) ORAZ do zmiany go w mapę
Domyślniie będzie zostawał jako ROW musi być jakiś automatyzm dodany
szczególnie dla zagnieżdżonych