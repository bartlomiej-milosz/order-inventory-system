# Order & Inventory Management System — PROJECT_SPEC (Lite)

> **Status:** projekt w budowie; specyfikacja opisuje docelowe v1.0, a nie ukończone funkcje.  
> **Cel:** projekt na zaliczenie modelowania baz danych i SQL oraz portfolio Java.  
> **Zasada:** mały modularny monolit, dokładnie **8 tabel domenowych**, pełny i poprawny workflow zamówienia.

## 1. Zakres i decyzja o modelu

Backend B2B dla pracowników obsługujących klientów, produkty, magazyny i zamówienia. Potwierdzenie zamówienia **rezerwuje** towar; dopiero realizacja zmniejsza fizyczny stan. Anulowanie zwalnia rezerwację, bez ruchu fizycznego.

Utrzymujemy wiele magazynów, ale **jedno zamówienie dotyczy dokładnie jednego magazynu**. Realizujemy lub anulujemy zawsze całe zamówienie. Ilości są całkowite, ceny w PLN; nie modelujemy VAT. Nie ma częściowych realizacji, wygasania rezerwacji ani ponownego otwierania zamówień.

**Poza v1.0:** kategorie produktów, płatności, faktury, wysyłka, zakupy od dostawców, transfery między magazynami, zwroty po realizacji, wiele walut, panel klienta, frontend, mikroserwisy i integracje ERP.

**Decyzja po przeglądzie 2026-11-16:** wcześniejszy diagram z jednym magazynem i odjęciem zapasu przy potwierdzeniu nie odpowiadał workflow. Zachowujemy workflow specyfikacji, ale usuwamy osobną tabelę `stock_reservations`: rezerwacje wynikają z pozycji zamówień o statusie `CONFIRMED`. Licznik `stock_levels.reserved` aktualizujemy transakcyjnie. Dzięki temu model ma 8 zamiast 9 tabel. Ta decyzja jest poprawna dla całych zamówień bez niezależnego cyklu życia rezerwacji.

Źródłem zasad jest ten dokument; [`schema-design.drawio`](schema-design.drawio) przedstawia ten sam model. Implementacja bazy powstanie w migracjach Flyway w M2. Tabela techniczna historii Flyway nie należy do 8 tabel domenowych; wymagania uczelni trzeba potwierdzić z prowadzącym.

## 2. Role i granice dostępu

- **ADMIN:** zarządza kontami pracowników i danymi katalogowymi; może wykonywać wszystkie operacje magazynu i zamówień.
- **OPERATOR:** przegląda katalog, stany i historię; tworzy/edytuje zamówienia robocze, potwierdza, realizuje, anuluje i rejestruje przyjęcia. Nie zarządza kontami, katalogiem ani korektami.
- **Klient:** rekord biznesowy, nie konto logowania. `created_by` i `performed_by` pochodzą z uwierzytelnionego użytkownika, nigdy z dowolnej wartości przesłanej przez klienta API.
- Jedna rola na konto: `ADMIN` lub `OPERATOR`; brak osobnych tabel ról i uprawnień.
- Uwierzytelnianie pracowników musi działać przed udostępnieniem operacji biznesowych. Obecne `denyAll()` to zabezpieczenie szkieletu, nie gotowy mechanizm logowania.

## 3. Model relacyjny — dokładnie 8 tabel

Nazwy tabel w liczbie mnogiej; schemat PostgreSQL: `inventory`. Każde `id` to `uuid`, generowane przez aplikację. Wszystkie pola są `NOT NULL`, chyba że oznaczono je `NULL`. `created_at` ma domyślnie `now()`, `active` domyślnie `true`. `updated_at` ustawia aplikacja przy zmianie liczników.

| Tabela | Kolumny (typ PostgreSQL i ograniczenia) |
|---|---|
| `users` | `id uuid PK`, `email varchar(254) UQ`, `password_hash text`, `role varchar(20)`, `active boolean`, `created_at timestamptz` |
| `customers` | `id uuid PK`, `name varchar(200)`, `email varchar(254)`, `phone varchar(30) NULL`, `created_at timestamptz` |
| `products` | `id uuid PK`, `sku varchar(50) UQ`, `name varchar(200)`, `unit_price numeric(12,2)`, `active boolean`, `created_at timestamptz` |
| `warehouses` | `id uuid PK`, `code varchar(30) UQ`, `name varchar(100)`, `active boolean` |
| `stock_levels` | `id uuid PK`, `product_id uuid FK → products`, `warehouse_id uuid FK → warehouses`, `on_hand integer DEFAULT 0`, `reserved integer DEFAULT 0`, `updated_at timestamptz` |
| `sales_orders` | `id uuid PK`, `order_number varchar(30) UQ`, `customer_id uuid FK → customers`, `warehouse_id uuid FK → warehouses`, `created_by uuid FK → users`, `status varchar(20) DEFAULT 'DRAFT'`, `created_at timestamptz`, `confirmed_at timestamptz NULL`, `note text NULL` |
| `order_items` | `id uuid PK`, `order_id uuid FK → sales_orders`, `product_id uuid FK → products`, `product_name varchar(200)`, `product_sku varchar(50)`, `quantity integer`, `unit_price numeric(12,2)` |
| `stock_movements` | `id uuid PK`, `stock_level_id uuid FK → stock_levels`, `performed_by uuid FK → users`, `order_id uuid FK → sales_orders NULL`, `type varchar(20)`, `quantity_delta integer`, `created_at timestamptz`, `note text NULL` |

### Ograniczenia bazy

- `UNIQUE (product_id, warehouse_id)` w `stock_levels`; `UNIQUE (order_id, product_id)` w `order_items`.
- `CHECK (on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand)`.
- `CHECK (quantity > 0)`; ceny produktów i pozycji `>= 0`; `quantity_delta <> 0`.
- `role IN ('ADMIN', 'OPERATOR')`; `status IN ('DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED')`.
- Typy ruchów: `IN` (dodatnia ilość, brak `order_id`), `OUT` (ujemna ilość, wymagane `order_id`), `ADJUSTMENT` (niezerowa ilość dowolnego znaku, brak `order_id`, niepusta notatka). Jeden `CHECK` powinien uwzględniać typ, znak i obecność powiązania.
- `confirmed_at`: `NULL` dla `DRAFT`, wymagane dla `CONFIRMED` i `FULFILLED`; dla `CANCELLED` zachowuje informację, czy zamówienie było wcześniej potwierdzone.
- Wszystkie FK: `ON DELETE RESTRICT`. Produkty, magazyny i konta dezaktywujemy. Nie usuwamy zamówień ani historii; pozycje można usuwać tylko w `DRAFT`.
- Indeksujemy FK, których nie pokrywa istniejący indeks z odpowiednim prefiksem: m.in. `stock_levels.warehouse_id`, `order_items.product_id`, trzy FK zamówienia i trzy FK ruchu. Nie dublujemy indeksów tworzonych przez PK/UQ.
- Identyfikatory e-mail kont normalizujemy do małych liter przed zapisem i logowaniem; ten sam format wymuszamy walidacją bazy. Nazwy i kody muszą być niepuste; SKU i kod magazynu mają jeden ustalony format w API.

`available = on_hand - reserved` jest wartością wyliczaną. Nie zapisujemy trzeciego licznika. Całość zamówienia obliczamy jako `SUM(quantity * unit_price)`; cena pozycji oraz `product_name` i `product_sku` są snapshotami. Przy dodaniu pozycji kopiujemy je z produktu. Zmiana ilości nie przelicza ceny; po potwierdzeniu pozycje są niezmienne. Kolejna próba dodania tego samego produktu daje `409`; ilość zmienia się przez edycję istniejącej pozycji.

### Spójność między tabelami

Są to reguły usługi transakcyjnej oraz testów integracyjnych, a nie zwykłych `CHECK` pojedynczego wiersza:

- `reserved` dla produktu i magazynu = suma ilości pozycji zamówień `CONFIRMED` dla tej pary.
- `on_hand` = suma `quantity_delta` ruchów danego `stock_level_id`; początkowy stan 0, pierwszy zapas wprowadzamy przez `IN`.
- Ruch `OUT` dotyczy magazynu z powiązanego zamówienia i produktu występującego w jego pozycjach; zapisujemy jeden ruch na pozycję podczas realizacji.
- Każde zamówienie może mieć 0..N pozycji w `DRAFT`, ale potwierdzenie wymaga przynajmniej jednej. Każda pozycja należy do dokładnie jednego zamówienia i produktu.
- Brak `stock_levels` dla pary produkt–magazyn oznacza dostępność 0. Pierwsze przyjęcie tworzy ten wiersz z zerowymi licznikami przez `INSERT ... ON CONFLICT DO NOTHING`, a następnie blokuje i aktualizuje go w tej samej transakcji. Nie tworzymy z góry wszystkich kombinacji produktów i magazynów.

## 4. Workflow i transakcje

| Akcja | Przejście | `on_hand` | `reserved` | Historia fizycznego stanu |
|---|---|---|---|---|
| Utworzenie/edycja | `DRAFT` | bez zmian | bez zmian | brak |
| `confirm` | `DRAFT → CONFIRMED` | bez zmian | `+ quantity` | brak |
| `fulfill` | `CONFIRMED → FULFILLED` | `- quantity` | `- quantity` | `OUT` na każdą pozycję |
| `cancel` | `DRAFT → CANCELLED` | bez zmian | bez zmian | brak |
| `cancel` | `CONFIRMED → CANCELLED` | bez zmian | `- quantity` | brak |
| Przyjęcie | bez zmiany zamówienia | `+ quantity` | bez zmian | `IN` |
| Korekta | bez zmiany zamówienia | `+ delta` | bez zmian | `ADJUSTMENT` |

1. `DRAFT` wymaga aktywnego magazynu. Dodanie pozycji i potwierdzenie wymagają aktywnych produktów i magazynu. Po potwierdzeniu nie zmieniamy klienta, magazynu ani pozycji. Dezaktywacja nie blokuje realizacji lub anulowania już potwierdzonego zamówienia.
2. `confirm` sprawdza dostępność **wszystkich** pozycji. Brak stanu lub niedostępność choć jednej pozycji oznacza `409` i rollback całości, także wcześniej zwiększonych liczników. Ustawia `confirmed_at`.
3. `FULFILLED` i `CANCELLED` są stanami końcowymi. Ponowne `confirm`, `fulfill`, `cancel` dla niedozwolonego przejścia zwracają `409`, bez dodatkowych skutków. Anulowanie zachowuje wcześniejsze `confirmed_at`.
4. Przyjęcia i korekty wymagają aktywnego produktu i magazynu. Korekta nie może obniżyć `on_hand` poniżej `reserved`. Historia jest tylko dopisywana; błędy korygujemy nowym ruchem.
5. Każda operacja biznesowa jest jedną transakcją. **Najpierw blokujemy `sales_orders` (`SELECT ... FOR UPDATE`), następnie potrzebne `stock_levels` w stałej kolejności według `id`.** Edycja nagłówka i pozycji DRAFT też blokuje zamówienie: nie może ścigać się z potwierdzeniem.
6. Przyjęcia/korekty blokują stan, bez blokowania zamówienia. Powstanie nowego wiersza stanu zabezpiecza UQ i upsert; nie stosujemy niesynchronizowanego „sprawdź, potem dodaj”. Wszystkie ścieżki zapisujące liczniki używają tej samej dyscypliny blokad.
7. Status sprawdzamy ponownie po uzyskaniu blokady. Zmiana statusu, obu liczników i wpisy historii zatwierdzają się razem. Testy obejmują zarówno konkurujące zamówienia, jak i równoległe akcje na **tym samym** zamówieniu.
8. Używamy PostgreSQL `READ COMMITTED` z jawnymi blokadami wierszy; nie dokładamy kolejek, cache ani osobnego systemu rezerwacji. Zmiana tej strategii wymaga uzasadnienia i testu.

## 5. Minimalne API

- Prefiks `/api/v1`; DTO i walidacja wejścia, listy z paginacją.
- Klienci, produkty, magazyny: lista/szczegóły, tworzenie i aktualizacja przez ADMIN; dezaktywacja produktów i magazynów zamiast kasowania historii.
- Stany według magazynu/produktu: `on_hand`, `reserved`, `available`; przyjęcia, korekty i historia ruchów.
- Zamówienia: tworzenie DRAFT, edycja nagłówka/pozycji w DRAFT, lista filtrowana po statusie/kliencie/magazynie, szczegóły, `confirm`, `fulfill`, `cancel`.
- Konta pracowników: ADMIN tworzy i dezaktywuje konto, nadaje jedną rolę; bez samodzielnej rejestracji i odzyskiwania hasła w v1.0. Pierwsze konto tworzy udokumentowana procedura lokalna, bez hasła w migracji.
- Błędy: `400` walidacja, `401` brak/błędne uwierzytelnienie, `403` brak uprawnień, `404` brak rekordu, `409` konflikt reguł/unikalności/statusu. Bez stack trace w odpowiedzi.
- OpenAPI/Swagger oraz przykładowe żądania wystarczą na demo. Prosty HTTP Basic przez HTTPS jest wystarczający; bez JWT, refresh tokenów i tabel sesji. Konfigurację CSRF dobieramy jawnie do sposobu użycia API, nie wyłączamy ochrony automatycznie tylko dlatego, że API jest REST.

## 6. Stack i architektura

- **Java 25**, Spring Boot i wersje zależności z `pom.xml`; Maven Wrapper. Nie zmieniamy stacku przy przeglądzie modelu.
- PostgreSQL 17 (wspólny obraz dla Compose i Testcontainers), Flyway, JPA/Hibernate z `ddl-auto: validate`, `open-in-view: false`.
- JUnit Jupiter, Mockito, Spring Boot Test i Testcontainers. Dokładne wersje zarządza konfiguracja Maven.
- Pakiety: `catalog`, `inventory`, `orders`, `users`, `shared`. Kontroler odpowiada za HTTP, serwis za reguły i transakcje, repozytorium za dostęp do danych. DTO nie są encjami.
- Migracje kwalifikują obiekty `inventory.*`, ponieważ domyślny schemat Flyway to `flyway_history`. DDL na uczelnię pochodzi z tych samych migracji, bez osobnej ręcznie utrzymywanej kopii.
- Docker Compose i GitHub Actions. VPS jest opcjonalnym ostatnim etapem; lokalne demo wystarcza do v1.0. Publiczne demo: HTTPS i fikcyjne dane.

## 7. Testy wymagane do v1.0

- Migracje na pustym PostgreSQL, PK/FK/UQ/CHECK/NOT NULL oraz mapowania JPA.
- Reguły statusów, ilości, cen, snapshotów i uprawnień.
- Rollback potwierdzenia, gdy zapasu brakuje tylko dla jednej z kilku pozycji.
- Dwa zamówienia konkurujące o ostatnie sztuki; niezależne transakcje i deterministyczna synchronizacja testu, bez polegania na `sleep`.
- Równoległe confirm/confirm, fulfill/cancel i edycja DRAFT/confirm dla tego samego zamówienia.
- Równoległe pierwsze przyjęcia tej samej pary produkt–magazyn: jeden stan i oba ruchy.
- Rezerwacja i anulowanie nie tworzą ruchów fizycznych; realizacja tworzy je dokładnie raz. Zgodność liczników z sumą ruchów i potwierdzonych pozycji.
- Próba korekty poniżej rezerwacji, błędne przejścia i brak uprawnień nie zmieniają danych.
- CI: `./mvnw verify`, budowa obrazu i smoke test Compose. Format/lint tylko jeśli rzeczywiście skonfigurowany.

## 8. Zasady współpracy (SDD Lite)

1. Przeczytaj specyfikację i milestones. Implementuj aktywny etap; jawne polecenie przeglądu/zmiany planu może obejmować całość dokumentacji.
2. Przed kodowaniem przedstaw krótko zakres, regułę, wpływ na bazę i weryfikację. Nie rozszerzaj domeny ani liczby tabel bez uzgodnienia.
3. Zmiana **zaimplementowanej bazy** wymaga nowej migracji. Sama poprawka diagramu przed pierwszym DDL nie wymaga pozornej migracji. Nie przepisuj migracji użytej w trwałej/współdzielonej bazie.
4. Testuj zmienioną logikę; nie usuwaj testów ani constraintów, aby ukryć błąd. Nie deklaruj wykonania testów, których nie uruchomiono.
5. Nie zapisuj sekretów ani danych rzeczywistych. Zachowuj cudze zmiany robocze. Dokumentuj ważne decyzje krótko.
6. Raportuj wynik, ograniczenia i kolejne kroki. Nie oznaczaj etapu jako zakończony bez kryteriów akceptacji i checkpointu zrozumienia właściciela.

## 9. Granica ukończenia

**v1.0:** 8 tabel, cały workflow rezerwacji/realizacji/anulowania, przyjęcia i korekty, bezpieczna współbieżność, role pracowników, testy, OpenAPI, Docker Compose, CI i powtarzalne demo z dokumentacją. Żaden dodatkowy moduł nie jest wymagany. Portfolio opisuje tylko rzeczywiście zaimplementowane i sprawdzone funkcje.
