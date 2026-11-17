# Order & Inventory Management System — MILESTONES

> **Źródło zasad:** [`PROJECT_SPEC.md`](PROJECT_SPEC.md).
> **Model:** [`schema-design.drawio`](schema-design.drawio), dokładnie 8 tabel domenowych.
> **Aktualny etap:** M1 — domknięcie modelu; potem M2 — pierwsze migracje. Część infrastruktury M3/M9 już istnieje.

## Jak czytać postęp

Checkboksy oznaczają sprawdzone rezultaty, nie sam zamiar. Wcześniej przygotowana infrastruktura nie oznacza ukończenia domeny. Każdy etap kończy się demonstracją i krótkim wyjaśnieniem decyzji przez właściciela projektu. Po większej funkcji wykonaj samodzielnie małą zmianę lub test.

**Stan przeglądu 2026-11-16:** istnieją Spring Boot, Maven Wrapper, profile local/deploy, Dockerfile, dwa pliki Compose, konfiguracja CI i 2 testy infrastruktury. `./mvnw verify` przechodzi. Nie ma jeszcze migracji domenowych, encji, repozytoriów domenowych, serwisów zamówień ani uwierzytelniania pracowników. Samo `ddl-auto: validate` przy braku encji nie weryfikuje docelowego modelu.

## M0 — Granice i repozytorium

- [X] Repozytorium, README i `docs/` istnieją.
- [X] Specyfikacja określa 8 tabel, role i workflow całego zamówienia.
- [ ] Właściciel potrafi wyjaśnić różnicę między fizycznym stanem, rezerwacją i dostępnością.
- [X] Sprawdzić rzeczywiste wymagania prowadzącego: liczba tabel, notacja, wymagane zapytania i forma oddania SQL. Nie zakładać, że nasze ustalenia są wymaganiami uczelni.

**Akceptacja:** znany zakres i wymagania zaliczenia; brak częściowych realizacji, zwrotów, płatności i frontendów.

## M1 — Model bazy

- [X] Diagram 8 tabel zgodny ze specyfikacją: `users`, `customers`, `products`, `warehouses`, `stock_levels`, `sales_orders`, `order_items`, `stock_movements`.
- [X] Opisane PK/FK, kardynalności, unikalności, typy PostgreSQL i CHECK.
- [X] Słownik kolumn i zasady spójności znajdują się w specyfikacji; uwagi na drugiej stronie diagramu.
- [ ] Przejść ręcznie scenariusze przyjęcia → potwierdzenia → realizacji oraz potwierdzenia → anulowania, zapisując wartości obu liczników.
- [ ] Uzasadnić snapshoty pozycji oraz przechowywanie liczników; wyjaśnić rezerwacje wynikające ze statusu i brak osobnej tabeli rezerwacji.
- [ ] Zweryfikować model pod wymagania prowadzącego i przygotować wymagany eksport/format.

**Akceptacja:** model da się odtworzyć z dokumentacji; autor rozumie relacje oraz odróżnia ograniczenia bazy od reguł serwisu.

## M2 — SQL, migracje i dane

- [ ] Utworzyć pierwszą migrację Flyway w `src/main/resources/db/migration/`: 8 tabel `inventory.*`, PK/FK/UQ/CHECK/NOT NULL i indeksy.
- [ ] Traktować migracje jako jedyne źródło DDL; jeśli uczelnia wymaga jednego skryptu, wygenerować go z migracji i sprawdzić na pustej bazie.
- [ ] Testować migracje i błędne dane na PostgreSQL przez Testcontainers; test musi wykryć brak tabel/constraintów, nie tylko istnienie historii Flyway.
- [ ] Przygotować osobny, jawnie uruchamiany fixture SQL z fikcyjnymi klientami, produktami, dwoma magazynami i zamówieniami. Liczniki muszą zgadzać się z pozycjami/ruchami; brak prawdziwych haseł i automatycznego zasiewania kont demo w deploy.
- [ ] Napisać SQL: JOIN, LEFT JOIN, GROUP BY/HAVING, agregacje, filtrowanie i sortowanie; dostępność, wartość zamówienia, uzgodnienie `reserved` z potwierdzonymi pozycjami oraz `on_hand` z ruchami.
- [ ] Wykazać rollback transakcji i zachowanie constraints; testować przypadek tego samego produktu w różnych magazynach.

**Akceptacja:** migracje odtwarzają model na pustej bazie, testy odrzucają błędne dane, a zapytania mają sprawdzone wyniki. Nie powstaje drugi, rozbieżny DDL.

## M3 — Domknięcie szkieletu i podstaw dostępu

- [X] Java 25, Spring Boot, Maven Wrapper i profile środowiskowe istnieją.
- [X] Konfiguracja Flyway/JPA i test połączenia z PostgreSQL działają.
- [X] Compose i Dockerfile istnieją; README opisuje start lokalny.
- [ ] Zmapować encje potrzebne w następnych etapach; sprawdzić `ddl-auto: validate` na rzeczywistych migracjach.
- [ ] Wprowadzić pakiety `catalog`, `inventory`, `orders`, `users`, `shared` w miarę dodawania funkcji, bez pustych warstw.
- [ ] Dodać logowanie pracowników z `users`, hashowanie hasła, sprawdzanie `active` oraz role ADMIN/OPERATOR. Opisać utworzenie pierwszego ADMIN bez hasła w repo.
- [ ] Ustalić HTTP Basic/HTTPS, politykę CSRF i spójne błędy 400/401/403/404/409. Dodać test uwierzytelnienia i odmowy dostępu.
- [ ] Zastępować `denyAll()` jawnymi regułami tylko dla wdrażanych endpointów; zachować domyślną odmowę dla reszty.

**Akceptacja:** migracje i mapowania działają, tożsamość pracownika jest dostępna w serwisach, a kolejne funkcje można wywołać i przetestować z odpowiednią rolą. Nie odkładamy całego logowania do M8.

## M4 — Katalog, przyjęcia i korekty

- [ ] Klienci, produkty i magazyny: odczyt dla obu ról, tworzenie/edycja dla ADMIN, dezaktywacja zamiast kasowania historii.
- [ ] Odczyt `on_hand`, `reserved`, wyliczonego `available`; brak wiersza danej pary oznacza 0.
- [ ] Przyjęcie `IN` dla ADMIN/OPERATOR; utworzenie stanu przez upsert, blokada i aktualizacja w jednej transakcji.
- [ ] Korekta `ADJUSTMENT` tylko dla ADMIN, z obowiązkową notatką; nie może zejść poniżej `reserved`.
- [ ] Zapisywać `performed_by` z uwierzytelnienia. Historia tylko do odczytu, bez endpointu edycji/usuwania ruchów.
- [ ] Dodać paginację, walidację, testy uprawnień, rollbacku i równoległych pierwszych przyjęć tej samej pary.

**Akceptacja:** można przygotować dane i przyjąć towar; każdy ruch ma autora, liczniki zgadzają się z historią, błędne korekty nie zmieniają danych.

## M5 — Zamówienia DRAFT

- [ ] Tworzyć zamówienie klienta w jednym aktywnym magazynie; `created_by` z uwierzytelnienia, unikalny numer.
- [ ] Dodawać/edytować/usuwać pozycje tylko w DRAFT; kopiować cenę, nazwę i SKU. Edycja ilości nie zmienia snapshotów.
- [ ] Jeden produkt raz w zamówieniu; nieprawidłowe ilości, duplikaty i nieaktywne produkty dają kontrolowany błąd.
- [ ] Każda edycja nagłówka lub pozycji blokuje wiersz zamówienia przed sprawdzeniem statusu.
- [ ] Lista/szczegóły, filtrowanie status/klient/magazyn, paginacja i wyliczony total; testy workflow i ról.

**Akceptacja:** DRAFT nie zmienia stanów ani rezerwacji, a snapshoty nie podążają za późniejszą zmianą katalogu.

## M6 — Potwierdzenie i rezerwacja

- [ ] Najpierw blokować zamówienie i ponownie sprawdzać DRAFT oraz niepuste pozycje.
- [ ] Blokować potrzebne `stock_levels` w kolejności `id`; sprawdzić aktywność i dostępność wszystkich pozycji.
- [ ] Zwiększyć `reserved`, ustawić CONFIRMED i `confirmed_at` w jednej transakcji. **Bez** ruchu fizycznego i osobnych rekordów rezerwacji.
- [ ] Brak jednego stanu/towaru wycofuje całą operację; potwierdzone pozycje są niezmienne.
- [ ] Test dwóch zamówień o ostatnie sztuki, dwóch potwierdzeń tego samego zamówienia oraz edycji DRAFT równoległej z confirm. Użyć niezależnych transakcji i synchronizacji zamiast `sleep`.

**Akceptacja:** brak oversellingu i podwójnej rezerwacji; `reserved` odpowiada sumie potwierdzonych pozycji, `on_hand` pozostaje bez zmian, a częściowa porażka nie zostawia skutków.

## M7 — Realizacja i anulowanie

- [ ] `CONFIRMED → FULFILLED`: pod blokadami odjąć ilości z `on_hand` i `reserved`, zapisać OUT na każdą pozycję z poprawnym zamówieniem, stanem i autorem.
- [ ] `DRAFT → CANCELLED`: tylko status. `CONFIRMED → CANCELLED`: zmniejszyć `reserved`, bez zmiany `on_hand` i bez ruchów fizycznych.
- [ ] Stany końcowe i ponowne akcje zwracają 409 bez nowych skutków.
- [ ] Testy rollbacku, uzgodnienia liczników oraz wyścigu fulfill/cancel tego samego zamówienia; jedna akcja wygrywa.
- [ ] Sprawdzić możliwość domknięcia potwierdzonego zamówienia po dezaktywacji jego produktu/magazynu.

**Akceptacja:** oba pełne scenariusze działają, historia odpowiada zmianom fizycznym, a akcje nie wykonują się podwójnie.

## M8 — Domknięcie API i bezpieczeństwa

- [ ] Zarządzanie kontami przez ADMIN: tworzenie, zmiana roli i dezaktywacja; bez publicznej rejestracji/resetu hasła.
- [ ] Pełna macierz testów ADMIN/OPERATOR/anonimowy/nieaktywne konto dla gotowych endpointów.
- [ ] Spójne błędy i obsługa naruszeń unikalności; brak ujawniania SQL, haseł i stack trace.
- [ ] Aktualne OpenAPI i przykłady całego workflow. Sprawdzić politykę dokumentacji oraz zdrowia w profilach local/deploy.

**Akceptacja:** każda funkcja ma określone uprawnienia, a dokumentacja pokazuje faktyczne API. Ten etap rozszerza podstawy z M3.

## M9 — Weryfikacja inżynierska i CI

- [X] Konfiguracja GitHub Actions zawiera Maven verify oraz budowę i smoke test Compose.
- [ ] Potwierdzić zielony pipeline na docelowym commicie; istnienie pliku workflow nie jest dowodem udanego uruchomienia CI.
- [ ] Przejrzeć transakcje, kolejność blokad, N+1, indeksy i paginację.
- [ ] Sprawdzić powtarzalność testów integracyjnych/współbieżności i uruchomienie z pustej bazy.
- [ ] Sprawdzić izolację profili local/deploy, stan readiness przy niedostępnej bazie, brak sekretów w artefaktach.
- [ ] Jeśli dodano format/lint, uruchamiać go w CI; obecnie nie deklarować nieistniejącej kontroli.
- [ ] Samodzielnie przejrzeć jeden pełny przepływ i wyjaśnić granice transakcji.

**Akceptacja:** rzeczywisty pipeline przechodzi, testy są powtarzalne, brak znanych błędów blokujących.

## M10 — Demo i portfolio

- [ ] README opisuje aktualny model, architekturę, decyzje, ograniczenia i start z czystej bazy.
- [ ] Fikcyjne dane demonstracyjne i przykładowe żądania/cURL od przyjęcia towaru po realizację/anulowanie.
- [ ] Powtarzalne demo w Compose. VPS opcjonalny; sprawdzenie przykładu deploy z zewnętrznym PostgreSQL/TLS jest osobnym zadaniem, nie wynika z testu local.
- [ ] Jeśli demo jest publiczne: HTTPS, fikcyjne dane, kontrola dostępu i sprawdzona konfiguracja sekretów.
- [ ] Opis portfolio/CV obejmuje tylko rzeczywiście wykonane i zrozumiane elementy.

**Akceptacja:** inna osoba odtwarza podstawowy scenariusz według README; autor potrafi wyjaśnić najważniejsze decyzje.

## M11 — v1.0

- [ ] Z pustej bazy działają migracje i jawnie uruchamiane dane demo.
- [ ] Działają przyjęcie → DRAFT → CONFIRMED → FULFILLED oraz CONFIRMED → CANCELLED → odzyskanie dostępności.
- [ ] Zgodność liczników z historią/rezerwacjami i brak oversellingu potwierdzają testy.
- [ ] Testy, CI, role, OpenAPI i dokumentacja są aktualne.
- [ ] Autor bez agenta wyjaśnia model, snapshoty, transakcje, blokady, statusy i uruchomienie.
- [ ] Utworzyć release `v1.0.0`; kolejne funkcje to opcjonalny backlog.

**Akceptacja:** ukończony obowiązkowy zakres; nie dokładamy modułów jako nowego warunku zakończenia.

## Definition of Done — zadanie

- Zachowanie odpowiada specyfikacji i aktywnemu etapowi.
- Implementowana zmiana bazy ma migrację; zmieniona logika ma adekwatne testy. Aktualizacja projektu przed pierwszym DDL nie jest implementacją bazy.
- Podano faktyczny wynik uruchomionej weryfikacji oraz jej ograniczenia.
- Dokumentacja odpowiada zmianie; nie dodano zbędnych warstw lub zależności.
- Właściciel potrafi wyjaśnić rezultat i samodzielnie wykonać drobną zmianę.
