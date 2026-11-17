# Przegląd spójności projektu — 2026-11-16

## Wynik

Szkielet aplikacji przechodzi obecną weryfikację. Główny problem dotyczył rozbieżności między dokumentami: wcześniejszy diagram nie modelował rezerwacji ani wielu magazynów opisanych w specyfikacji. Ujednolicono diagram, specyfikację, milestones i opis statusu w README. Model ma **8 tabel domenowych**; implementacja domeny pozostaje do wykonania.

## Poprawione problemy

| Problem | Decyzja / poprawka |
|---|---|
| Diagram odejmował stan przy potwierdzeniu; specyfikacja rezerwowała i wydawała przy realizacji | Zachowany workflow specyfikacji: confirm zwiększa reserved, fulfill zmniejsza oba liczniki, cancel zwalnia reserved bez ruchu fizycznego |
| Jeden magazyn i kategorie w diagramie kontra wiele magazynów w specyfikacji | Dodane warehouses i para produkt–magazyn w stock_levels; kategorie poza zakresem |
| 9 tabel i osobna reprezentacja każdej rezerwacji mimo braku częściowych realizacji/wygaśnięcia | 8 tabel; rezerwacja wynika z CONFIRMED order_items, reserved jest utrzymywanym transakcyjnie licznikiem |
| Brak autora ruchu i inne nazwy ról/tabel | performed_by → users; ADMIN/OPERATOR; nazwy tabel w liczbie mnogiej zgodne we wszystkich dokumentach |
| Plan opisywał blokady zapasu, ale nie serializację operacji na tym samym zamówieniu | Najpierw blokada sales_orders, potem stock_levels w stałej kolejności; także edycja DRAFT blokuje zamówienie |
| Nieopisany wyścig pierwszego przyjęcia tej samej pary | UNIQUE(product_id, warehouse_id), upsert zerowego stanu, blokada i zapis ruchu w jednej transakcji |
| Uwierzytelnianie dopiero po implementacji większości API, podczas gdy szkielet blokuje wszystkie jego ścieżki | Podstawy logowania i ról w M3, autoryzacja razem z funkcjami, pełna macierz w M8 |
| Osobne DDL uczelniane i późniejsze migracje mogły się rozjechać | Flyway jest jednym źródłem SQL od M2; wszystkie obiekty jawnie w inventory, eksport na uczelnię z tych migracji |
| Nieaktualny obraz postępu | Milestones rozróżniają istniejącą infrastrukturę od niezaimplementowanej domeny; nie oznaczono całych etapów jako zakończonych |
| Kopia robocza edytora draw.io pojawiała się jako plik projektu | Dodane wzorce kopii .drawio.bkp/.drawio.dtmp do .gitignore, bez usuwania istniejących kopii |

## Co sprawdzono

- `./mvnw -B -ntp verify`: **BUILD SUCCESS**, 2 testy, 0 błędów, 0 pominiętych. Testcontainers uruchomił PostgreSQL 17.12. Testy dotyczą połączenia, inicjalizacji schematów/historii Flyway, health i anonimowego dostępu.
- `docker compose config --quiet` dla local i deploy: oba pliki poprawne składniowo; użyto wartości zastępczych, bez odczytu lokalnych sekretów.
- Zbudowano Dockerfile i uruchomiono pełny profil local w **osobnym projekcie Compose**, z osobną bazą, losowym hasłem i wolnymi portami. `/livez` i `/readyz` zwróciły UP, `/v3/api-docs` zwróciło dokument OpenAPI. Zasoby tego testu usunięto; istniejącego stosu użytkownika nie używano.
- Przejrzano pom.xml, wrapper, kod aplikacji i testów, profile, Dockerfile, Compose, CI, Dependabot i pliki ignorowania.
- Diagram: 8 tabel, 10 relacji FK, zgodne nazwy/typy kolumn z tabelą w PROJECT_SPEC; sprawdzenie XML i podgląd obu stron po eksporcie w draw.io.

## Pozostałe zadania i granice weryfikacji

1. **Następny krok: M1 → M2.** Właściciel powinien prześledzić oba scenariusze liczników i potwierdzić wymagania prowadzącego. Następnie pierwsza migracja i testy rzeczywistych constraintów. Nie dodano migracji podczas tego przeglądu projektu.
2. Obecne 2 testy nie dowodzą poprawności przyszłych rezerwacji, autoryzacji ani schematu domenowego. Na razie nie ma żadnej migracji domenowej ani encji; samo poprawne uruchomienie Flyway/Hibernate nie weryfikuje 8 tabel.
3. Profil deploy został przejrzany i jego Compose zwalidowany, ale **nie** uruchomiono go przeciw zewnętrznemu PostgreSQL z TLS, osobnymi rolami i sekretami. Sprawdzić ten wariant przy rzeczywistym wdrożeniu; VPS nie blokuje v1.0.
4. Nie uruchomiono zdalnego GitHub Actions ani nie potwierdzono jego stanu dla commita. Lokalnie wykonano odpowiadające mu build/test/smoke. Repo ma już wiele niezacommitowanych zmian; zachowano je i nie wykonywano commit/push.
5. Testy profili local/deploy, braku gotowości przy awarii bazy oraz pełne testy uprawnień pozostają w M8/M9. Nie dodawano testów dla samej redakcji dokumentacji.
6. Nie wykonano audytu bezpieczeństwa zależności ani formalnej weryfikacji wymagań uczelni. To przegląd modelu, planu i obecnego szkieletu, nie potwierdzenie ukończenia produktu.

## Dlaczego bez osobnej tabeli rezerwacji

Każda pozycja potwierdzonego zamówienia rezerwuje całą swoją ilość w magazynie nagłówka. Nie ma innego statusu lub ilości rezerwacji niż wynikające z zamówienia. Dodatkowa tabela powielałaby ten stan i wymagała synchronizacji. Obecny model pozwala odtworzyć aktywne rezerwacje zapytaniem przez sales_orders/order_items i uzgodnić je z reserved. Jeśli kiedyś dojdą częściowe realizacje, niezależne wygasanie lub dzielenie pozycji między magazyny, ta decyzja będzie wymagała ponownego przeglądu — te funkcje nie należą do v1.0.
