# Poczta

Prywatna aplikacja pocztowa na Androida, łącząca się bezpośrednio z serwerem obsługującym IMAP i SMTP. Aplikacja nie używa Discorda ani Google.

## Pierwsza wersja

- logowanie do skrzynki przez adres e-mail, hasło aplikacji oraz hosty i porty IMAP/SMTP;
- pobieranie wiadomości z folderu INBOX i wysyłanie zwykłych wiadomości tekstowych;
- hasło szyfrowane kluczem Android Keystore; wiadomości nie są zapisywane w lokalnej bazie;
- GitHub Actions buduje testowy plik APK, który można pobrać z artefaktów danego uruchomienia.

Potrzebna jest usługa pocztowa z dostępem IMAP/SMTP. W wielu usługach trzeba wygenerować osobne hasło aplikacji. Domyślne porty to IMAP 993 i SMTP 587; port SMTP 465 również jest obsługiwany.

## Pobieranie APK

Po uruchomieniu workflow Android APK otwórz przebieg w zakładce Actions i pobierz artefakt `poczta-android-apk`. To testowy APK podpisany kluczem debug; nie jest to publikacja w Google Play.

## Ograniczenia pierwszej wersji

Brak jeszcze obsługi załączników, OAuth, wyszukiwania i folderów innych niż INBOX. Nie podawaj hasła do poczty w repozytorium ani w rozmowie — wpisuje się je wyłącznie w aplikacji.