# Order & Inventory System — pytania rekrutacyjne

Do powtórki wybierz sekcję i odpowiedz na głos, zanim przeczytasz odpowiedź. Po każdym kolejnym etapie dopisz tutaj pytania o to, co zostało wdrożone, dlaczego działa w ten sposób i jak zostało sprawdzone. Do decyzji projektowych możesz wrócić w [specyfikacji](PROJECT_SPEC.md), a do postępu w [milestones](MILESTONES.md).

## Fundament aplikacji — przed pierwszą migracją

### Zakres i uruchomienie lokalne

#### 1. Co robi ten projekt i co już działa?

To backend do obsługi zamówień i stanów magazynowych. Na tym etapie działa szkielet Spring Boot z PostgreSQL, konfiguracją migracji, sondami stanu i testami integracyjnymi. Workflow CI jest skonfigurowany, a jego kroki sprawdziłem lokalnie. Model domeny ma osiem zaprojektowanych tabel. Migracje tych tabel, encje, logowanie pracowników i operacje zamówień są kolejnymi etapami.

#### 2. Co się dzieje od uruchomienia aplikacji do odpowiedzi `UP` na `/readyz`?

Uruchamiam bazę, a przy starcie Javy przekazuję profil `local` i zmienne z `.env`. Spring konfiguruje połączenie, Flyway sprawdza historię i wykonuje oczekujące migracje, a Hibernate inicjalizuje JPA. Gdy Spring zgłasza gotowość, a baza odpowiada, `/readyz` zwraca `200` i `{"status":"UP"}`.

#### 3. Dlaczego są trzy pliki `application*.yaml`?

`application.yaml` zawiera ustawienia wspólne, np. sposób uruchamiania Flyway. Profil `local` dokłada lokalne połączenie z bazą i włącza Swaggera. Profil `deploy` dokłada ustawienia serwerowe, w tym połączenie z bazą przez TLS i osobne dane logowania dla Flyway.

#### 4. Co oznacza `Failed to configure a DataSource: 'url' attribute is not specified`?

W moim przypadku log pokazał `No active profile set`. URL PostgreSQL-a mam w `application-local.yaml`, więc przy starcie trzeba aktywować profil `local`. Przy poprawnym adresie i wyłączonym PostgreSQL Flyway przerywa start, gdy próbuje połączyć się z bazą, żeby sprawdzić historię.

#### 5. Czym różnią się `make start-db`, `make run-local` i `make start-stack`?

`start-db` uruchamia w Dockerze sam PostgreSQL, np. do pracy z IDE. `run-local` uruchamia tę bazę, wczytuje `.env` i startuje Javę na Macu przez Maven. `start-stack` uruchamia bazę i Javę w kontenerach. Profil Compose `app` włącza usługę aplikacji, a profil Springa `local` wybiera ustawienia samej Javy. [Makefile](../Makefile)

#### 6. Jak Spring dostaje wartości z `.env`?

`make run-local` wykonuje `set -a`, a potem `. ./.env`. Powłoka eksportuje odczytane zmienne, więc Maven i uruchomiona przez niego Java dostają np. `DB_PASSWORD`. Przy starcie z IDE trzeba przekazać te same zmienne w konfiguracji uruchomienia.

#### 7. Dlaczego Java raz łączy się z `127.0.0.1:5432`, a raz z `db:5432`?

Java na Macu korzysta z portu PostgreSQL wystawionego na hoście. Java w kontenerze korzysta z nazwy usługi `db` we wspólnej sieci Compose. Z kolei `127.0.0.1` użyte w healthchecku aplikacji oznacza sam kontener aplikacji, bo tam wykonuje się `curl`. [Compose lokalny](../compose.yaml)

### Baza, Flyway i JPA

#### 8. Co robi Flyway, a co `ddl-auto: validate`?

Flyway stosuje zmiany bazy zapisane w SQL w `src/main/resources/db/migration/`. Hibernate z `ddl-auto: validate` sprawdza zgodność mapowań encji z istniejącą bazą przy starcie. Na tym etapie Flyway inicjalizuje historię, a sprawdzenie docelowych mapowań będzie możliwe po dodaniu encji.

#### 9. Po co są schematy `inventory` i `flyway_history`? Co robię ze zmianą po wykonaniu `V1`?

`inventory` to schemat na tabele domenowe, a `flyway_history.flyway_schema_history` trzyma rejestr migracji. Jeśli po zastosowaniu `V1` na trwałej bazie potrzebuję zmienić kolumnę, dopisuję `V2`. Flyway porównuje m.in. sumy kontrolne wykonanych migracji z odpowiadającymi im plikami.

#### 10. Co robi `open-in-view: false`?

Spring nie utrzymuje otwartego kontekstu JPA przez całe żądanie HTTP. Przykładowo po pobraniu `Order` leniwie ładowane pozycje mogłyby wymagać osobnego zapytania. Z tym ustawieniem dane odpowiedzi pobieram w serwisie i składam DTO, zanim wyjdę z transakcji. Ten przykład przećwiczę po dodaniu relacji `Order`–`OrderItem`.

### Security, sondy i wdrożenie

#### 11. Co zabezpiecza Spring Security przed dodaniem logowania?

Reguły HTTP pozwalają anonimowo wywołać `GET /livez` i `GET /readyz`, a z profilem `local` również ścieżki Swaggera. Pozostałe żądania trafiają na regułę `denyAll()`. Wykluczenie `UserDetailsServiceAutoConfiguration` zapobiega utworzeniu domyślnego konta `user` z losowym hasłem. [Reguły w kodzie](../src/main/java/dev/bartlomiejmilosz/inventory/config/SecurityConfiguration.java)

#### 12. Czym różnią się `/livez` i `/readyz`?

`/livez` pokazuje stan działania aplikacji. `/readyz` mówi, czy aplikacja jest gotowa na ruch, i dodatkowo uwzględnia PostgreSQL. Gdy baza padnie po starcie, `/readyz` może zwrócić `503`, podczas gdy `/livez` nadal zwraca `200`. W obu odpowiedziach wystawiam tylko ogólny status.

#### 13. Dlaczego Swagger jest włączony lokalnie?

Swagger UI ułatwia podejrzenie dokumentacji i ręczne wywołanie API w trakcie pracy. Na wdrożeniu dokumentacja jest domyślnie wyłączona. Dostęp do operacji biznesowych kontrolują reguły Spring Security.

#### 14. Dlaczego Flyway i aplikacja mają osobne konta bazy na wdrożeniu?

Lokalnie Flyway korzysta z tego samego `DataSource` co aplikacja. Na wdrożeniu oba połączenia prowadzą do tej samej bazy, ale Flyway loguje się rolą, która może zmieniać schemat, a aplikacja ma uprawnienia do pracy z danymi. `create-schemas: false` oznacza, że schematy przygotowuję wcześniej, a migracje tworzą w nich obiekty.

#### 15. Jak przekazujesz hasła i weryfikujesz połączenie z bazą na wdrożeniu?

Compose montuje hasła jako pliki w `/run/secrets/`. Spring przez `configtree` odczytuje nazwę pliku jako nazwę ustawienia, a jego treść jako wartość. JDBC z `sslmode=verify-full` szyfruje połączenie, sprawdza zaufanie do certyfikatu serwera i sprawdza, czy ten certyfikat pasuje do nazwy hosta PostgreSQL. [Compose wdrożeniowy](../compose.deploy.yaml)

#### 16. Po co Dockerfile ma etap budowania i etap uruchomieniowy?

Pierwszy etap ma JDK i buduje plik JAR przez Maven. Drugi zawiera środowisko potrzebne do uruchomienia JAR-a i startuje Javę jako użytkownik bez uprawnień roota. W profilu `deploy` Compose uruchamia ten obraz i kieruje go do osobno przygotowanego PostgreSQL-a. [Dockerfile](../Dockerfile)

### Testy, formatowanie i CI

#### 17. Po co Testcontainers i jak test dostaje adres bazy?

Testcontainers pozwala uruchomić test na tej samej wersji PostgreSQL, co w lokalnym Compose, bez korzystania z mojej lokalnej bazy. Spring przez `@ServiceConnection` dostaje dane połączenia do tymczasowego kontenera. Aplikacja w teście startuje na losowym porcie HTTP. [Konfiguracja testów](../src/test/java/dev/bartlomiejmilosz/inventory/config/TestcontainersConfiguration.java)

#### 18. Co dokładnie sprawdzają obecne testy?

Pierwszy sprawdza połączenie z PostgreSQL 17 i obecność schematu `inventory` oraz tabeli historii Flyway. Drugi wysyła prawdziwe żądania HTTP i sprawdza status sond oraz odmowę dostępu do wybranych ścieżek. Na tym etapie są to testy infrastruktury. Reguły zamówień będą miały osobne testy po implementacji.

#### 19. Co robi workflow CI i jak naprawiasz błąd formatowania?

CI sprawdza styl Javy przez Spotless z `google-java-format`, waliduje konfigurację Compose dla wdrożenia, uruchamia testy i buduje obraz. Następnie startuje lokalny stos Compose i sprawdza, czy odpowiada `/v3/api-docs`. `make format-java` nakłada format lokalnie, a `make verify-build` sprawdza format i uruchamia weryfikację Mavena. [Workflow CI](../.github/workflows/ci.yaml)
