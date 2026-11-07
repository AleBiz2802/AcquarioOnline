# AcquarioOnline

App Android (Kotlin, Jetpack Compose, Material 3, architettura MVVM) per gestire acquari collegati a un broker MQTT.
I dati di ogni acquario (nome, Wi-Fi, password, IP del broker) vengono inviati a un Arduino via Bluetooth LE.

## Come avviarla

1. Apri la cartella `AcquarioOnline` in **Android Studio** e lascia fare il Gradle sync
   (serve connessione a Internet la prima volta, per scaricare Gradle e le librerie).
2. Avvia su un **dispositivo fisico** con il debug USB attivo: l'emulatore non ha il Bluetooth LE.
   Il login e la gestione di sezioni e acquari funzionano anche su emulatore, solo l'invio all'Arduino no.

Non serve nessuna configurazione online né nessun account.

## Struttura (MVVM)

```
data/         modelli Room, DAO, repository, profili utente, BLE   (Model)
ui/*Screen    composable senza logica                               (View)
ui/*ViewModel stato (StateFlow) ed eventi                           (ViewModel)
```

| Flusso | Dove |
|---|---|
| Crea profilo / accedi (email e password) | `ui/login`, `data/auth` |
| Home con floating button → "Nuova sezione" (nome + broker MQTT) | `ui/home` |
| Burger icon → drawer con le sezioni | `ui/home/HomeScreen.kt` |
| Sezione → bottone "Aggiungi acquario" → form | `ui/section` |
| Invio via Bluetooth | `data/ble/BleProvisioner.kt` |

I "fragment" richiesti sono implementati come **bottom sheet** (`ModalBottomSheet`), l'equivalente Compose.

## Profili

I profili sono **salvati solo sul telefono** (database Room). La password non è mai salvata in chiaro:
si conserva solo un hash PBKDF2-HMAC-SHA256 con salt casuale. Ogni profilo vede solo le proprie sezioni.
Conseguenza: un profilo creato su un telefono non esiste su un altro, e non c'è il recupero password.

## Protocollo Bluetooth

L'app cerca il dispositivo filtrando per service UUID, quindi l'Arduino deve pubblicizzare il servizio
(`BLE.setAdvertisedService(dataService)`).

| Elemento | UUID | Uso |
|---|---|---|
| Service | `19B10010-E8F2-537E-4F6C-D104768A1214` | |
| Scrittura | `19B10011-E8F2-537E-4F6C-D104768A1214` | l'app scrive il payload (max 100 byte) |
| Notifica | `19B10012-E8F2-537E-4F6C-D104768A1214` | l'Arduino notifica un valore ≠ 0 a dati ricevuti |

**Formato del payload** (scelta mia, non indicata nella richiesta): quattro campi separati da ritorno a capo (`\n`)

```
nome acquario \n ssid \n password \n ip broker
```

Il ritorno a capo non si può digitare nei campi di testo, quindi non entra in conflitto con le password.
Se il tuo codice Arduino si aspetta un altro formato, basta cambiare `AquariumBle.buildPayload`.
L'acquario viene salvato nella sezione **solo dopo la conferma** dell'Arduino.

## Scelte da conoscere

- **La password del Wi-Fi non viene salvata**: serve solo per l'invio all'Arduino.
- Il form dell'acquario precompila l'IP del broker con quello della sezione (modificabile).
- Se nome + SSID + password + IP superano 100 byte, il form lo segnala prima di inviare.
- Su Android 11 o precedente la scansione BLE richiede anche la **posizione** attiva.
- Se i dati arrivano troncati, controlla che l'Arduino accetti la negoziazione dell'MTU (l'app richiede 185).
