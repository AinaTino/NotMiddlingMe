## 🧪 GUIDE DE TEST DES CORRECTIONS

Destiné à valider chaque correction et vérifier le bon fonctionnement de StopMiddlingMe.

---

## TEST #1 : VPN Connection Internet (Bug #1)

### Objectif
Vérifier que la connexion Internet fonctionne quand le VPN de détection est activé.

### Procédure
1. **Installer l'app** sur le téléphone Android
2. **Connecter à un réseau WiFi** (avec Internet)
3. **Ouvrir Facebook** et vérifier que la page charge normalement
4. **Activer la protection détection active** (toggle VPN)
5. **Rafraichir Facebook** ou **tenter plusieurs pages**
6. **Désactiver le VPN** et vérifier que Facebook charge toujours

### Résultats Attendus
- ✅ Facebook charge normalement SANS le VPN
- ✅ Facebook charge normalement AVEC le VPN activé
- ✅ Pas de timeout ou de "pas de connexion Internet"
- ✅ Les autres apps (WhatsApp, etc.) fonctionnent aussi pendant le VPN

### Si ÉCHOUE
- Vérifier les logs Logcat pour `StopMiddlingMeVpnService`
- Invoquer `SessionCleanupWorker.cancelSchedule(context)` pour désactiver le worker
- Vérifier que le DNS du VPN est correctement configuré

---

## TEST #2 : SessionCleanupWorker Stability (Bug #2)

### Objectif
Vérifier que l'app ne crash pas sur l'erreur Hilt Factory du Worker.

### Procédure
1. **Installer et lancer l'app**
2. **Ouvrir Logcat** (Android Studio)
3. **Filtrer sur : SessionCleanupWorker** ou **HiltWorkerFactory**
4. **Laisser l'app en arrière-plan pendant 1-2 minutes**
5. **Ouvrir de nouveau l'app** et vérifier qu'elle répond

### Résultats Attendus
- ✅ Pas de crash au lancement
- ✅ Pas de messages d'erreur critique dans Logcat
- ✅ L'app reste responsive

### Si ÉCHOUE
- Logcat montre `Could not instantiate ...SessionCleanupWorker` mais c'est OK (try-catch)
- Exécuter : `adb shell am crash com.arda.stopmiddlingme` pour forcer un crash propre
- Relancer l'app

---

## TEST #3 : LAN Scanner Completeness (Bug #3)

### Objectif
Vérifier que le scanner détecte tous les appareils du réseau local.

### Procédure
1. **Se connecter à un réseau WiFi** avec plusieurs appareils (ordi, routeur, autres phones, etc.)
2. **Ouvrir l'écran Scanner** dans l'app
3. **Taper sur "Lancer le scan"**
4. **Attendre 5-10 secondes**
5. **Comparer le nombre d'appareils** avec ce que montre votre routeur

### Résultats Attendus
- ✅ Au minimum 3-5 appareils détectés (routeur, téléphone, ...) 
- ✅ Chaque appareil affiche une IP, MAC, et idéalement un fabricant
- ✅ La gateway marquée avec une icône spéciale
- ✅ Votre téléphone marqué comme "Vous"

### Exemples d'Appareilss Detectés
```
Gateway        192.168.1.1    AA:BB:CC:11:22:33  Cisco (Router)
Votre Phone    192.168.1.42   A4:C3:F0:XX:XX:XX  Apple
Ordi           192.168.1.100  B4:7C:9C:XX:XX:XX  Samsung
Inconnu        192.168.1.55   AB:CD:EF:XX:XX:XX  (Autre)
```

### Si ÉCHOUE
- **Peu d'appareils vus :**
  - Les appareils doivent avoir communiqué avec le téléphone
  - Essayer de pinguer depuis l'ordi vers le téléphone
  - Attendre quelques secondes puis relancer le scan

- **Fichier /proc/net/arp lisible ?**
  ```bash
  adb shell cat /proc/net/arp | head -5
  ```
  Si aucune ligne → sans doute un device inconnnu. Essayer de rebooter.

---

## TEST #4 : Ettercap MITM Detection (Bug #4)

### Objectif Avancé ⚠️
Vérifier que l'app détecte une véritable attaque ARP Spoofing lancée par Ettercap ou similar.

### Prérequis
- **Deux machines au minimum :**
  1. Android phone (victime, avec l'app)
  2. Linux / Windows avec Ettercap / arpspoof installé (attaquant, même LAN)
- **Même réseau WiFi** pour les deux
- **DashboardScreen** de l'app visuelle

### Procédure
1. **Lancer le Monitoring** dans l'app (s'assurer que la baseline est créée)
2. **Noter la baseline gateway MAC** (ligne "Gateway MAC" affichée)
3. **Sur l'ordi attaquant, lancer Ettercap :**
   ```bash
   # Exemple simplifié
   sudo ettercap -G  # Mode GUI
   # Ou via arpspoof (plus simple)
   sudo arpspoof -i <interface> -t <phone_ip> <gateway_ip>
   ```
4. **Sur le téléphone, observer le Dashboard :**
   - Score augmente-t-il ? ✅
   - Notifications reçues ? ✅
   - Alerte s'affiche-t-elle (rouge CRITIQUE) ? ✅
5. **Arrêter Ettercap** et vérifier que l'app revient à SAFE après 30s

### Résultats Attendus
- ✅ **Immédiatement** (< 5 sec) : le score passe de 0 à 5+ (au minimum ARP_GATEWAY_CHANGE)
- ✅ Signal pour `ARP_GATEWAY_CHANGE` + description du changement MAC
- ✅ Si alternances rapides : signal pour `ARP_MAC_DUPLICATE` 
- ✅ **Notification** poussée à l'utilisateur
- ✅ **Écran Dashboard** passe en rouge (CRITIQUE)
- ✅ **Timeline** visible dans AlertDetail avec tous les signaux

### Logcat Debug (si besoin)
```bash
adb logcat | grep "ArpAnalyzer\|ARP_GATEWAY_CHANGE\|ARP_MAC_DUPLICATE"
```

### Si ÉCHOUE
**Probable :** la baseline n'existe pas pour ce réseau

- Solution :
  1. Connecter au WiFi
  2. Ouvrir Settings → marquer le réseau comme "de confiance"
  3. Réouvrir Dashboard pour créer la baseline
  4. Rélancer Ettercap

**Ou :** Ettercap n'atteint pas le téléphone

- Vérifier qu'Ettercap cible bien :
  - Source : IP du gateway (ex: 192.168.1.1)
  - Target : IP du téléphone (ex: 192.168.1.42)
  - Interface : la bonne carte réseau

---

## TEST #5 : Full Integration Test

Scénario complet : simule une attaque MITM réelle + vérification de la réponse.

### Procédure Complète
1. **L'app en train de monitoring**
2. **Network WiFi baseline créée**
3. **Activer protection VPN** (mais pas critique)
4. **Ouvrir Dashboard** - note le score initial (devrait être 0-1)
5. **Lancers Ettercap pour 30 secondes**
6. **Observer :**
   - Score monte rapidement
   - Signaux ARP s'accumulent
   - Alerte notification reçue
7. **Arrêter l'attaque**
8. **Attendre 60 secondes**
9. **Vérifier que le score redescend** à SAFE (après expiration des signaux)

### Timeline Attendue
```
T+0s    : Score = 0 (SAFE)
T+5s    : Ettercap lance ARP Spoofing
T+7s    : Score = 5 (ARP_GATEWAY_CHANGE détecté) → 🔴 CRITIQUE
T+10s   : Score = 10 (ARP_MAC_DUPLICATE ajouté)
T+35s   : Ettercap stoppé
T+40s   : Signaux ARP restent actifs (decay 60s)
T+70s   : Signaux expirés → Score = 0 → 🟢 SAFE
```

---

## 🛠️ Debugging Avancé

### Activer les Logs de Debug

**Ajouter dans `ArpAnalyzer.kt` :**
```kotlin
private fun checkGatewayMac(...) {
    val currentMac = table.find { it.ip == baseline.gatewayIp }?.mac ?: return
    Log.d("ArpAnalyzer", "Gateway ${baseline.gatewayIp}: baseline=${ baseline.gatewayMac}, current=$currentMac")
    ...
}
```

**Ajouter dans `StopMiddlingMeVpnService.kt` :**
```kotlin
protocol == 17 && dstPort == 53 -> {
    Log.d("VpnService", "DNS paquet laissé passer (size=$length)")
    outputMutex.withLock { outputStream.write(data, 0, length) }
}
```

### Lire l'ARP Table Directement

```bash
# Terminal sur PC
adb shell cat /proc/net/arp

# Résultat attendu
IP address       HW type     Flags       HW address            Mask     Device
192.168.1.1     0x1         0x2         AA:BB:CC:11:22:33     *        wlan0
192.168.1.42    0x1         0x2         A4:C3:F0:XX:XX:XX     *        wlan0
192.168.1.50    0x1         0x2         AB:CD:EF:XX:XX:XX     *        wlan0
```

### Vérifier le VPN Tunnel

```bash
# Vérifier que le TUN est créé
adb shell ip link show | grep tun

# Résultat si VPN actif
52: tun0: <POINTOPOINT,UP,LOWER_UP> mtu 1280 qdisc pfifo_fast state UP mode DEFAULT group default qlen 500
    link/none
```

### Reset Complet (Nuclear Option)

```bash
# Arrêter tous les services
adb shell am force-stop com.arda.stopmiddlingme

# Effacer la base de données
adb shell rm /data/data/com.arda.stopmiddlingme/databases/*

# Relancer
adb shell am start -n com.arda.stopmiddlingme/.MainActivity
```

---

## 📊 Résumé du Statut des Tests

Créer un tableau pour tracer le statut :

| Test | Description | Status | Date | Notes |
|------|-------------|--------|------|-------|
| #1 | VPN Internet | ⏳ TODO | - | Tester avec Facebook |
| #2 | WorkManager | ⏳ TODO | - | Vérifier pas de crash |
| #3 | LAN Scanner | ⏳ TODO | - | Scanner avec outils |
| #4 | Ettercap MITM | ⏳ TODO | - | Attaque test |
| #5 | Full Integration | ⏳ TODO | - | Scénario complet |

---

**Dernière mise à jour:** Mai 6, 2026

