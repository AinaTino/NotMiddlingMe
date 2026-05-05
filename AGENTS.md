# NotMiddlingMe — Guide Complet
## Système Mobile de Détection de MITM sur le Réseau Local

> **Document de référence complet** — Théorie, Modélisation, Architecture, Implémentation  
> Projet Android — ENI Fianarantsoa  
> Stack : Kotlin · Jetpack Compose · Room · VpnService · Coroutines · Flow

---

# TABLE DES MATIÈRES

1. [Introduction & Contexte](#1-introduction--contexte)
2. [Théorie Réseau — Les Fondations](#2-théorie-réseau--les-fondations)
   - 2.1 [Le Modèle OSI & le voyage d'un paquet](#21-le-modèle-osi--le-voyage-dun-paquet)
   - 2.2 [HTTP, HTTPS et TLS](#22-http-https-et-tls)
   - 2.3 [ARP Spoofing & DNS Spoofing](#23-arp-spoofing--dns-spoofing)
   - 2.4 [Les Contraintes Android](#24-les-contraintes-android)
3. [Threat Model — Ce qu'on détecte](#3-threat-model--ce-quon-détecte)
4. [Modèle de Détection — Le Cœur Intellectuel](#4-modèle-de-détection--le-cœur-intellectuel)
5. [Modélisation Merise — MCD & MLD](#5-modélisation-merise--mcd--mld)
6. [Modélisation UML — Diagrammes](#6-modélisation-uml--diagrammes)
   - 6.1 [Cas d'utilisation](#61-cas-dutilisation)
   - 6.2 [Diagrammes de Séquence](#62-diagrammes-de-séquence)
   - 6.3 [Diagramme de Classes](#63-diagramme-de-classes)
7. [UI/UX — Wireframes & Design System](#7-uiux--wireframes--design-system)
8. [Architecture Technique](#8-architecture-technique)
9. [Guide d'Implémentation](#9-guide-dimplémentation)
10. [Limites & Faux Positifs](#10-limites--faux-positifs)

---

# 1. Introduction & Contexte

## Qu'est-ce qu'un MITM ?

Une attaque **Man-in-the-Middle (MITM)** consiste pour un attaquant à s'insérer discrètement entre deux parties qui communiquent — typiquement entre un appareil mobile et sa passerelle réseau — afin d'intercepter, lire, ou modifier le trafic sans que ni l'une ni l'autre des parties ne s'en aperçoive.

```
SITUATION NORMALE
─────────────────
Téléphone ──────────────────────► Gateway ──► Internet

SITUATION MITM
──────────────
Téléphone ──► Attaquant ──► Gateway ──► Internet
              ↑
              lit tout, peut modifier
```

## Pourquoi c'est dangereux

Sur un réseau local (WiFi d'université, café, hôtel), un attaquant sur le même réseau peut :

- **Lire** tout le trafic HTTP non chiffré (mots de passe, sessions)
- **Modifier** des pages web à la volée (injection de code malveillant)
- **Rediriger** le trafic vers de faux serveurs
- **Intercepter** des sessions chiffrées si le certificat TLS est compromis

## Objectif du projet

> **NotMiddlingMe** est un système mobile de détection de MITM sur le réseau local.  
> L'application surveille en temps réel les indicateurs d'attaque sur le réseau WiFi auquel l'appareil est connecté, alerte l'utilisateur avec un score de confiance, et fournit un historique exploitable.

## Ce que l'app EST et ce qu'elle N'EST PAS

```
L'app EST :
✅ Un système de détection d'anomalies réseau côté client
✅ Un moniteur passif et actif du comportement réseau local
✅ Un outil d'alerte et de traçabilité

L'app N'EST PAS :
❌ Un firewall (elle ne bloque pas les attaques, elle les détecte)
❌ Un outil offensif (aucune capacité d'attaque)
❌ Un moniteur du trafic des autres appareils (impossible sans root)
❌ Une protection contre les MITM sur 4G/5G
```

---

# 2. Théorie Réseau — Les Fondations

## 2.1 Le Modèle OSI & le voyage d'un paquet

### Le modèle OSI — version utile

Le modèle OSI définit 7 couches d'abstraction réseau. Pour ce projet, les couches pertinentes sont :

```
┌─────────────────────────────────┐
│  APPLICATION  │  HTTP, DNS      │  ← Ce que l'utilisateur voit
├───────────────┼─────────────────┤
│  TRANSPORT    │  TCP / UDP      │  ← Comment les données sont transmises
├───────────────┼─────────────────┤
│  RÉSEAU       │  IP             │  ← Adressage logique (routing)
├───────────────┼─────────────────┤
│  LIAISON      │  ARP, MAC       │  ← Adressage physique (réseau local)
└─────────────────────────────────┘
```

**Règle fondamentale :** chaque couche encapsule la précédente. Les données descendent les couches à l'envoi, les remontent à la réception.

### Les concepts clés

#### IP — l'adresse logique

L'adresse IP identifie une machine sur un réseau. Elle peut changer (assignée par DHCP à chaque connexion).

```
192.168.1.42   ← adresse privée (réseau local)
8.8.8.8        ← adresse publique (serveur Google)

Plages d'adresses PRIVÉES (jamais routées sur Internet) :
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
```

#### MAC — l'adresse physique

L'adresse MAC est l'identité matérielle de la carte réseau. Normalement unique au monde, gravée dans le hardware.

```
Format : AA:BB:CC:DD:EE:FF
         └──────┘ └──────┘
         OUI         NIC
    (constructeur) (unique)

Les 3 premiers octets (OUI) identifient le constructeur :
B4:7C:9C → Samsung
A4:C3:F0 → Apple
```

#### Port — l'adresse de l'application

```
IP = l'immeuble
Port = le numéro d'appartement

Ports importants pour ce projet :
│ Port │ Protocole │ Pertinence                    │
│ 53   │ DNS       │ résolution de domaine (UDP)   │
│ 80   │ HTTP      │ trafic non chiffré             │
│ 443  │ HTTPS     │ trafic chiffré TLS            │
```

#### TCP vs UDP

**TCP** — fiable, avec confirmation :
```
Client → SYN        →  Serveur
Client ← SYN-ACK   ←  Serveur
Client → ACK        →  Serveur
       [connexion établie, données échangées]
```
Utilisé par HTTP, HTTPS — tout ce qui doit arriver intact.

**UDP** — rapide, sans confirmation :
```
Client → DATA → Serveur (pas de confirmation)
```
Utilisé par DNS — vitesse primordiale sur les requêtes de résolution.

### Le voyage d'une requête — cas concret

Quand tu ouvres `https://google.com` sur ton téléphone :

```
ÉTAPE 1 — Résolution DNS (UDP port 53)
  Téléphone → "C'est quoi l'IP de google.com ?"
  Serveur DNS → "C'est 142.250.x.x"

ÉTAPE 2 — TCP Handshake (vers 142.250.x.x:443)
  SYN → SYN-ACK → ACK

ÉTAPE 3 — TLS Handshake
  Échange de certificats, établissement du chiffrement

ÉTAPE 4 — Requête HTTP (chiffrée dans TLS)
  GET / HTTP/1.1

ÉTAPE 5 — Réponse HTTP (chiffrée)
  La page HTML
```

> **Point d'insertion MITM :** entre les étapes 1 et 2 (DNS Spoofing), ou pendant le TLS handshake (faux certificat).

### ARP — le protocole fondamental de l'attaque

Sur le réseau local, pour envoyer un paquet à `192.168.1.1` (la gateway), ton téléphone doit connaître son **adresse MAC**. ARP fait cette résolution :

```
Broadcast → "Qui a l'IP 192.168.1.1 ?"
                    ↓
Gateway répond → "C'est moi, MAC = AA:BB:CC:11:22:33"
```

Ton téléphone stocke ça dans sa **table ARP** (`/proc/net/arp` sur Android/Linux).

**Le problème fondamental d'ARP :**
- Stateless — pas de notion d'état ou de session
- Non authentifié — n'importe qui peut répondre
- Accepte les réponses non sollicitées (Gratuitous ARP)

Conçu en 1982. La sécurité réseau n'existait pas comme concept à l'époque. On vit encore avec ce problème aujourd'hui.

---

## 2.2 HTTP, HTTPS et TLS

### HTTP — le protocole nu

HTTP transporte du texte brut sur TCP. Tout est lisible par quiconque intercepte la connexion.

```
Requête HTTP :
GET /login HTTP/1.1
Host: example.com
Cookie: session=abc123

Réponse HTTP :
HTTP/1.1 200 OK
Set-Cookie: session=xyz789
<html>...</html>
```

Si quelqu'un est entre toi et le serveur, il lit tout. Mot de passe inclus.

### HTTPS — HTTP avec chiffrement TLS

HTTPS = HTTP + TLS. Le contenu est chiffré. Mais :

```
Ce que TLS protège :
✅ Contenu de la requête
✅ Headers HTTP
✅ Cookies
✅ Corps de la réponse

Ce que TLS ne cache PAS :
❌ L'IP du serveur (visible dans les headers réseau)
❌ Le SNI (nom de domaine, souvent visible en clair)
```

### Le TLS Handshake — étape par étape

```
Client                          Serveur
  │                                │
  │── ClientHello ───────────────► │  "Voici les algos que je supporte"
  │                                │
  │◄── ServerHello ────────────── │  "On utilise TLS 1.3, AES-256"
  │◄── Certificate ────────────── │  "Voici mon certificat signé"
  │                                │
  │  [Client vérifie le certificat]│
  │                                │
  │── Finished ───────────────────►│  "Clé de session établie"
  │◄── Finished ──────────────────│  "Confirmé"
  │                                │
  │════ Données chiffrées ════════►│
```

### Les certificats TLS

Un certificat est un document signé qui dit :
> *"Je suis bien google.com, et la CA (Certificate Authority) DigiCert/Let's Encrypt/etc. le confirme."*

Ton téléphone a une liste de **CA de confiance** préinstallées (environ 150). Quand il reçoit un certificat, il vérifie :

1. Le domaine correspond-il ? (`google.com` == `google.com`)
2. Est-ce signé par une CA de confiance ?
3. Est-ce encore valide (date d'expiration) ?

Si une vérification échoue → `NET::ERR_CERT_AUTHORITY_INVALID`

### Les attaques TLS

#### HTTPS Downgrade / SSL Stripping

```
Client ──── HTTPS ────► [MITM] ──── HTTP ────► Serveur
```

L'attaquant répond au client en HTTP alors que le serveur est en HTTPS. Le client envoie ses données en clair à l'attaquant.

Protection côté serveur : **HSTS** (HTTP Strict Transport Security) — le navigateur/app n'accepte que HTTPS pour ce domaine.

#### Faux Certificat

```
Client ──► [MITM] ◄──── TLS légitime ────► Serveur
              │
              └──── TLS avec FAUX CERT ────► Client
```

L'attaquant crée son propre certificat pour `google.com`. Le téléphone rejette — sauf si l'attaquant a installé sa CA dans le keystore système (ce que font Burp Suite, mitmproxy pour les tests).

---

## 2.3 ARP Spoofing & DNS Spoofing

### ARP Spoofing — l'attaque mère

#### Situation normale

```
Réseau : 192.168.1.0/24

Téléphone   192.168.1.10  →  MAC: AA:AA:AA:AA:AA:AA
Gateway     192.168.1.1   →  MAC: BB:BB:BB:BB:BB:BB
Attaquant   192.168.1.50  →  MAC: CC:CC:CC:CC:CC:CC

Table ARP du téléphone (normale) :
192.168.1.1  →  BB:BB:BB:BB:BB:BB  ✅
```

#### L'attaque

L'attaquant envoie des **Gratuitous ARP Reply** (non sollicités) aux deux victimes :

```
→ Au téléphone :
  "192.168.1.1 est à CC:CC:CC:CC:CC:CC"  (faux)

→ À la gateway :
  "192.168.1.10 est à CC:CC:CC:CC:CC:CC"  (faux)
```

Table ARP du téléphone après empoisonnement :

```
192.168.1.1  →  CC:CC:CC:CC:CC:CC  ☠️ (MAC de l'attaquant)
```

Résultat :

```
Téléphone ──► Attaquant ──► Gateway ──► Internet
           ◄──           ◄──

L'attaquant voit et peut modifier TOUT le trafic.
```

#### Signaux de détection ARP Spoofing

```
Signal 1 — Changement de MAC de la gateway
  Avant : 192.168.1.1 → BB:BB:BB:BB:BB:BB
  Après : 192.168.1.1 → CC:CC:CC:CC:CC:CC  ← CRITIQUE

Signal 2 — Même MAC pour deux IP différentes
  192.168.1.1   → CC:CC:CC:CC:CC:CC
  192.168.1.10  → CC:CC:CC:CC:CC:CC  ← CRITIQUE
  (un seul équipement répond pour deux IPs = MITM positionné)

Signal 3 — Nouvelle entrée ARP inattendue
  Équipement jamais vu rejoint le réseau ← SUSPECT
```

### DNS Spoofing

#### Situation normale

```
Téléphone → "C'est quoi l'IP de facebook.com ?"
DNS (8.8.8.8) → "157.240.x.x"
Téléphone → connexion vers 157.240.x.x  ✅
```

#### L'attaque (via ARP Spoofing)

```
Téléphone ──► [requête DNS UDP] ──► Attaquant ──► (bloque la vraie requête)
                                         │
                                         └──► "facebook.com = 192.168.1.50" ☠️

Téléphone se connecte à 192.168.1.50 (machine de l'attaquant)
```

#### Le problème des faux positifs DNS

Les grands services (Google, Facebook, Cloudflare) ont des **centaines d'IP** pour un seul domaine :

```
google.com → 142.250.74.14   (requête depuis Madagascar)
google.com → 142.250.201.78  (requête depuis France)
```

C'est du **load balancing** et **géo-routing** — parfaitement normal. Donc comparer deux IPs exactes pour le même domaine n'est pas fiable.

#### Ce qui est VRAIMENT suspect en DNS

```
Cas 1 — IP privée pour un domaine public
  google.com → 192.168.1.50   ☠️ IMPOSSIBLE légitimement
  google.com → 10.0.0.1       ☠️ IMPOSSIBLE légitimement

Cas 2 — ASN différent
  IP reçue → appartient à AS15169 (Google LLC) ✅
  IP reçue → appartient à AS inconnu/local     ☠️

Cas 3 — TTL anormalement bas
  TTL normal : 300s
  TTL suspect : 0s ou 1s ← force le re-lookup constant
```

### DHCP Spoofing

#### Situation normale

```
Téléphone (nouvelle connexion) → Broadcast "Je cherche un serveur DHCP"
Serveur DHCP (routeur)         → "Voici ton IP, gateway=192.168.1.1, DNS=8.8.8.8"
```

Le téléphone configure automatiquement sa gateway et ses DNS à partir de cette réponse.

#### L'attaque

```
Téléphone → Broadcast DHCP Discover
Attaquant  → répond AVANT le vrai serveur DHCP (race condition)
             "gateway=192.168.1.50, DNS=192.168.1.50"

Téléphone configure :
  gateway → 192.168.1.50  ☠️ (machine de l'attaquant)
  DNS     → 192.168.1.50  ☠️ (contrôlé par l'attaquant)
```

Résultat identique à l'ARP Spoofing, mais sans toucher la table ARP — la victime **route volontairement** son trafic vers l'attaquant dès le départ.

#### Limite fondamentale de la détection DHCP côté full mobile

Le téléphone ne voit **jamais** le paquet DHCP lui-même. Il voit uniquement le résultat : `LinkProperties` mis à jour. Que ce soit le vrai serveur DHCP ou l'attaquant qui ait répondu en premier, le résultat est **identique** du point de vue du téléphone.

```
Attaquant répond en premier → LinkProperties change
Vrai serveur répond         → LinkProperties change
Admin change le routeur     → LinkProperties change
DHCP lease expire           → LinkProperties peut changer

→ Impossible de distinguer ces cas par le changement seul.
→ Un signal "gateway change" standalone = faux positif garanti.
```

#### Ce qu'on peut réellement détecter

**Signal 1 — LinkProperties change SANS événement de connexion**

C'est le seul signal DHCP fiable disponible sans root. Un changement de `LinkProperties` déclenché sans `onAvailable()` préalable indique qu'un paquet DHCP non sollicité a été envoyé pendant la session active.

```
Connexion normale :
  onAvailable() → onLinkPropertiesChanged()  ← attendu, ignoré

DHCP non sollicité (suspect) :
  onLinkPropertiesChanged() SANS onAvailable()  ← anormal
  Détecté via : NetworkCallback
```

**Signal 2 — DNS configuré = IP privée inconnue de la baseline**

```
DNS = 8.8.8.8 (baseline connue)    → rien
DNS = 192.168.1.50 (jamais vu)     → SUSPECT
Détecté via : LinkProperties.dnsServers
```

**Signal 3 — Gateway change + ARP doublon dans la foulée**

La vraie confirmation d'un DHCP Spoofing arrive quand `LinkProperties` change ET que `/proc/net/arp` montre le doublon MAC caractéristique. Les deux signaux ensemble rendent l'attaque quasi-certaine.

```
GATEWAY_CHANGE_UNSOLICITED (+3) + ARP_MAC_DUPLICATE (+5) = score 8 → CRITIQUE
```

> **Différence clé avec ARP Spoofing :** le cache ARP peut rester propre après un DHCP Spoofing — la victime route volontairement vers l'attaquant sans empoisonnement ARP. Les deux détections sont complémentaires et se renforcent mutuellement.

---

### IPv6 Rogue Router Advertisement

#### Contexte

Sur les réseaux IPv6, les routeurs s'annoncent via des **Router Advertisement (RA)** — messages ICMPv6 qui disent aux appareils : "je suis le routeur, utilisez-moi comme gateway IPv6".

Contrairement à ARP, c'est un mécanisme **actif** : le routeur envoie des RA périodiquement, et n'importe quel appareil peut en envoyer.

#### L'attaque

```
Attaquant → envoie un RA avec son adresse comme gateway IPv6
            "Routeur IPv6 = fe80::dead:beef"

Téléphone → accepte le RA
            configure gateway IPv6 = fe80::dead:beef  ☠️

Trafic IPv6 → passe par l'attaquant
```

Particulièrement dangereux car **beaucoup de réseaux ont IPv6 activé par défaut** mais les administrateurs ne le surveillent pas.

#### Signaux de détection IPv6 RA

```
Signal 1 — Nouvelle gateway IPv6 inconnue apparaît
  Baseline : pas de gateway IPv6 (réseau IPv4 only)
  Actuel   : gateway IPv6 = fe80::dead:beef  ← SUSPECT
  Détecté via : LinkProperties.routes (préfixe IPv6)

Signal 2 — Gateway IPv6 change
  Baseline : fe80::1 (gateway légitime connue)
  Actuel   : fe80::dead:beef  ← CRITIQUE
  Détecté via : LinkProperties.routes
```

> **Limite honnête :** certains réseaux légitimes ont IPv6 avec SLAAC (StateLess Address AutoConfiguration) — une gateway IPv6 qui apparaît n'est pas forcément malveillante. C'est pourquoi ce signal est standalone=false en isolation. Il devient critique en combinaison avec d'autres signaux.

---

### La chaîne d'attaque complète

```
ÉTAPE 1 : ARP Spoofing
────────────────────────────────────────────────────────────
Attaquant envoie faux ARP Reply
→ Table ARP victime empoisonnée
→ Attaquant = intermédiaire réseau

ÉTAPE 2 : DNS Spoofing (optionnel)
────────────────────────────────────────────────────────────
Attaquant intercepte requêtes DNS UDP
→ Répond avec fausse IP avant le vrai serveur DNS
→ Victime se connecte au faux serveur

ÉTAPE 3 : HTTPS Downgrade ou Faux Certificat
────────────────────────────────────────────────────────────
Attaquant intercepte le trafic TLS
→ Lit ou modifie les données chiffrées

RÉSULTAT : MITM complet, invisble pour la victime
```

---

## 2.4 Les Contraintes Android

### Ce qu'Android bloque (sans root)

Android est basé sur Linux mais son modèle de sécurité interdit délibérément l'accès réseau bas niveau :

```
❌ Raw sockets          → pas de capture de paquets bruts
❌ Accès direct ARP     → lecture/écriture table ARP impossible directement
❌ Mode promiscuous     → écouter le trafic des autres appareils
❌ ICMP raw             → ping custom bas niveau
❌ Ports < 1024         → ports système réservés
```

### Ce qu'Android autorise (sans root)

```
✅ VpnService           → intercepter TON propre trafic
✅ /proc/net/arp        → lire la table ARP (lecture seule)
✅ ConnectivityManager  → infos sur la connexion active
✅ WifiManager          → SSID, BSSID, signal, scan réseaux
✅ LinkProperties       → DNS, gateway, routes configurés
✅ NetworkCapabilities  → capabilities du réseau
✅ Sockets TCP/UDP      → connexions classiques
```

### VpnService — la pièce maîtresse

C'est le seul moyen légal sans root d'intercepter et analyser son propre trafic.

```
SANS VpnService :
App → Kernel Android → Interface WiFi → Réseau

AVEC VpnService :
App → [Interface TUN virtuelle] → Ton code → Interface WiFi → Réseau
                ↑
          Tu lis ici (FileDescriptor)
```

Le VpnService crée une **interface réseau virtuelle TUN**. Tout le trafic de l'appareil passe par ce tunnel. Tu lis les paquets, tu les analyses, tu les retransmes.

### APIs clés par fonctionnalité

| Fonctionnalité | API Android | Ce qu'on obtient |
|---|---|---|
| Table ARP | `/proc/net/arp` | IP → MAC de tous les devices vus |
| Infos WiFi | `WifiManager` | SSID, BSSID, force signal |
| Scan réseaux | `WifiManager.startScan()` | Liste AP environnants |
| DNS configurés | `LinkProperties.dnsServers` | Serveurs DNS actifs |
| Gateway | `LinkProperties.routes` | IP de la passerelle |
| Trafic complet | `VpnService + TUN` | Paquets IP bruts |

---

# 3. Threat Model — Ce qu'on détecte

Le threat model définit exactement le périmètre de détection. **Hors de ce périmètre, l'app ne prétend rien.**

## Les acteurs

```
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│    Victime      │   │   Attaquant     │   │    Gateway      │
│ (le téléphone) │   │ (sur le LAN)    │   │   (routeur)     │
│                 │   │                 │   │                 │
│ Android         │   │ Même réseau     │   │ Point d'accès   │
│ NotMiddlingMe   │   │ WiFi que victim │   │ légitime        │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

## Les vecteurs d'attaque formalisés

| Attaque | Précondition | Action attaquant | Effet observable côté victime |
|---|---|---|---|
| ARP Spoofing | Attaquant sur même LAN | Envoie faux ARP Reply | MAC gateway change dans `/proc/net/arp` |
| DHCP Spoofing | Attaquant sur même LAN | Répond au DHCP Discover avant le vrai serveur | Gateway + DNS changent dans `LinkProperties` |
| IPv6 Rogue RA | Attaquant sur même LAN | Envoie faux Router Advertisement ICMPv6 | Nouvelle gateway IPv6 inconnue dans `LinkProperties` |
| Rogue AP | Attaquant avec AP physique | Clone SSID, BSSID différent | Deux AP avec même SSID dans scan WiFi |
| DNS Spoofing | Attaquant en MITM via ARP | Répond aux queries DNS avant le vrai DNS | IP retournée = IP privée locale |
| SSL Stripping | Attaquant en MITM | Intercepte redirect HTTPS→HTTP | Requête HTTP vers domaine HTTPS-only |
| Faux certificat TLS | Attaquant en MITM | Présente son propre cert TLS | Cert self-signed ou CA inconnue |

---

# 4. Modèle de Détection — Le Cœur Intellectuel

## Pourquoi un score et pas une règle binaire

```
Mauvais modèle :
  SI mac_gateway_change ALORS alerte_critique
  → Trop de faux positifs (DHCP, reboot routeur)

Bon modèle :
  score = somme pondérée de plusieurs signaux corrélés
  → Décision basée sur contexte, pas sur un seul événement
```

## Les SignalTypes — définition statique

Chaque type de signal a des propriétés fixes. Ce sont des constantes dans le code, pas des données en base.

### La propriété `standalone` — explication

`standalone` répond à une seule question :
> **"Ce signal seul suffit-il à déclencher une alerte CRITIQUE ?"**

```
standalone = true  → ce signal seul = CRITIQUE immédiat
standalone = false → ce signal seul = SUSPECT au mieux,
                     il a besoin des autres pour peser
```

Certains signaux sont tellement anormaux qu'ils ne peuvent pas être légitimes, même isolés :

```
CERT_SELF_SIGNED sur google.com → standalone=true
  Google ne servira JAMAIS un cert self-signed → CRITIQUE seul

ARP_GATEWAY_CHANGE → standalone=true
  Le MAC de ta gateway change rarement sans raison → CRITIQUE seul
```

D'autres ont des causes légitimes possibles et ne valent rien sans contexte :

```
TTL_ANORMAL → standalone=false
  Cloudflare fait ça légitimement → contribue au score, jamais seul

IPV6_ROGUE_RA → standalone=false
  Certains réseaux activent IPv6 SLAAC par défaut → signal d'appui seulement
```

**Dans le code, standalone se traduit par :**

```kotlin
private fun computeLevel(score: Int, hasStandalone: Boolean): AlertLevel {
    if (hasStandalone && score >= 4) return AlertLevel.CRITIQUE
    return when (score) {
        in 0..3  -> AlertLevel.SAFE
        in 4..6  -> AlertLevel.SUSPECT
        in 7..9  -> AlertLevel.WARNING
        else     -> AlertLevel.CRITIQUE
    }
}

// Exemple A — standalone seul :
// CERT_SELF_SIGNED (+5, standalone=true) → hasStandalone=true → CRITIQUE direct

// Exemple B — non-standalone seuls :
// TTL(+1) + DNS_CHANGED(+3) + IPV6_RA(+3) = score 7, hasStandalone=false → WARNING

// Exemple C — mix :
// IPV6_RA(+3, false) + ARP_GATEWAY(+5, true) → hasStandalone=true → CRITIQUE
```

```kotlin
enum class SignalType(
    val poids: Int,
    val standalone: Boolean,  // ce signal seul → CRITIQUE ?
    val decaySeconds: Int     // durée de vie du signal
) {
    ARP_GATEWAY_CHANGE         (poids = 5, standalone = true,  decaySeconds = 60),
    ARP_MAC_DUPLICATE          (poids = 5, standalone = true,  decaySeconds = 60),
    ARP_NEW_ENTRY              (poids = 1, standalone = false, decaySeconds = 30),
    GATEWAY_CHANGE_UNSOLICITED (poids = 3, standalone = false, decaySeconds = 60),
    // LinkProperties change SANS onAvailable() → DHCP non sollicité
    GATEWAY_IP_CHANGED         (poids = 2, standalone = false, decaySeconds = 60),
    // Gateway différente de la baseline (faible, cause légitime possible)
    DNS_SERVER_UNKNOWN         (poids = 3, standalone = false, decaySeconds = 60),
    // DNS configuré = IP privée jamais vue en baseline
    IPV6_ROGUE_RA              (poids = 3, standalone = false, decaySeconds = 90),
    ROGUE_AP_BSSID             (poids = 4, standalone = true,  decaySeconds = 120),
    ROGUE_AP_SECURITY          (poids = 4, standalone = true,  decaySeconds = 120),
    ROGUE_AP_SIGNAL            (poids = 1, standalone = false, decaySeconds = 60),
    DNS_PRIVATE_IP             (poids = 4, standalone = true,  decaySeconds = 30),
    DNS_SERVER_CHANGED         (poids = 3, standalone = false, decaySeconds = 60),
    TTL_ANORMAL                (poids = 1, standalone = false, decaySeconds = 10),
    SSL_STRIP                  (poids = 3, standalone = false, decaySeconds = 20),
    CERT_SELF_SIGNED           (poids = 5, standalone = true,  decaySeconds = 30),
    CERT_UNKNOWN_CA            (poids = 4, standalone = true,  decaySeconds = 30),
    CERT_DOMAIN_MISMATCH       (poids = 5, standalone = true,  decaySeconds = 30),
    TRAFFIC_MIRROR             (poids = 4, standalone = true,  decaySeconds = 60)
}
```

## Les AlertSessions — fenêtre de corrélation

Quand un premier signal est détecté, une **AlertSession** s'ouvre. Tous les signaux suivants sur le même réseau et dans la fenêtre de temps sont rattachés à cette session.

```
T+0s  : ARP_GATEWAY_CHANGE (+5) → Session ouverte, fermeture prévue T+30s
T+3s  : DNS_PRIVATE_IP (+4)     → Rattaché à la session, fermeture repoussée T+33s
T+7s  : CERT_SELF_SIGNED (+5)   → Rattaché à la session, fermeture repoussée T+37s
T+37s : [silence]               → Session fermée automatiquement
Score total = 14 → CRITIQUE
```

### Les règles des sessions

```
Règle 1 — Isolation par réseau
  Deux signaux sur des réseaux différents → sessions séparées, jamais fusionnées.

Règle 2 — Fenêtre glissante
  Chaque nouveau signal reset le timer de fermeture.
  La session reste ouverte tant que les signaux s'enchaînent.
  Sans nouveau signal → session fermée après le délai d'expiration.

Règle 3 — Standalone ne signifie pas "session courte"
  Un signal standalone déclenche CRITIQUE immédiatement.
  Mais la session reste OUVERTE pendant toute sa durée normale (decay).
  Si d'autres signaux arrivent pendant ce temps → rattachés à la même session.
  → L'alerte est immédiate. La surveillance, elle, continue.

  T+0s  : ARP_GATEWAY_CHANGE (standalone=true) → CRITIQUE déclenché
  T+15s : DNS_PRIVATE_IP arrive               → rattaché à la même session
  T+60s : decay ARP_GATEWAY_CHANGE expire     → session se ferme si silence

Règle 4 — Fermeture manuelle
  L'utilisateur résout l'alerte → session fermée immédiatement, score remis à 0.
```

## Les seuils de décision

```
Score  0–3   →  🟢 SAFE     (bruit normal du réseau)
Score  4–6   →  🟡 SUSPECT  (surveiller, notifier discrètement)
Score  7–9   →  🟠 WARNING  (probable, notifier)
Score 10+    →  🔴 CRITIQUE (attaque quasi-certaine, action requise)

Exception : tout signal standalone ≥ 4 → CRITIQUE direct
            (même si score total < 10)
```

## Le decay — pourquoi c'est indispensable

```
Sans decay :
  Une alerte d'hier pollue le score aujourd'hui
  → faux positifs permanents
  → score qui ne redescend jamais

Avec decay :
  T+0s  : ARP_GATEWAY_CHANGE → +5 pts, expire à T+60s
  T+60s : signal expiré      → -5 pts, score revient à 0
  → L'état du réseau = une fenêtre glissante dans le temps
```

## Le score dans le contexte des chaînes d'attaque

La vraie puissance du scoring : un signal faible seul = rien. Le même signal dans une session déjà ouverte = bascule en CRITIQUE.

```
Exemple :

Scénario A — SSL Stripping seul :
  SSL_STRIP (+3) → score = 3 → SAFE (pas d'alerte)

Scénario B — SSL Stripping après ARP Spoofing :
  ARP_GATEWAY_CHANGE (+5) → session ouverte, score = 5 → CRITIQUE direct
  SSL_STRIP (+3) → score = 8, même session → CRITIQUE confirmé

→ L'app détecte la COORDINATION des attaques, pas juste les événements isolés.
```

---

# 5. Modélisation Merise — MCD & MLD

## Rappel des cardinalités

Une cardinalité exprime "combien d'occurrences d'une entité peuvent être liées à une occurrence de l'autre".

```
0,1  →  zéro ou un (optionnel, unique)
1,1  →  exactement un (obligatoire, unique)
0,N  →  zéro ou plusieurs (optionnel, multiple)
1,N  →  un ou plusieurs (obligatoire, multiple)
```

**Comment lire une relation :**
> "Un [entité A] peut être lié à combien de [entité B] ?"  
> "Un [entité B] peut être lié à combien de [entité A] ?"

## MCD — Modèle Conceptuel de Données

```
┌──────────────────────┐
│       RESEAU         │
├──────────────────────┤
│ #ssid                │
│  bssid               │
│  gatewayIp           │
│  gatewayMac          │
│  premiereConnexion   │
│  estDeConfiance      │
└──────┬───────────────┘
       │
       │ 1,1 ◄── "une baseline appartient à exactement un réseau"
       │
   possède
       │
       │ 1,1 ◄── "un réseau possède exactement une baseline active"
       │
┌──────▼───────────────┐
│      BASELINE        │
├──────────────────────┤
│ #id                  │
│  gatewayMac          │
│  gatewayIp           │
│  dnsServeurs         │
│  bssid               │
│  dateCapture         │
└──────────────────────┘


┌──────────────────────┐
│       RESEAU         │  (même entité)
└──────┬───────────────┘
       │
       │ 1,1 ◄── "une session vient d'un seul réseau"
       │
   génère
       │
       │ 0,N ◄── "un réseau génère 0 à N sessions d'alerte"
       │
┌──────▼───────────────┐
│    ALERT_SESSION     │
├──────────────────────┤
│ #id                  │
│  statut              │  (OPEN | CLOSED | RESOLVED)
│  scoreTotal          │
│  niveauFinal         │  (SAFE | SUSPECT | WARNING | CRITIQUE)
│  ouverteA            │
│  fermetureAuto       │
│  fermetureEffective  │
└──────┬───────────────┘
       │
       │ 1,1 ◄── "une instance de signal appartient à une session"
       │
   contient
       │
       │ 1,N ◄── "une session contient au moins 1 signal"
       │
┌──────▼───────────────┐
│   SIGNAL_INSTANCE    │
├──────────────────────┤
│ #id                  │
│  type                │  (référence à l'enum SignalType du code)
│  detail              │  "Gateway MAC: BB→CC"
│  timestamp           │
│  expireAt            │
└──────────────────────┘
```

### Justification des cardinalités

| Relation | Cardinalité | Justification |
|---|---|---|
| RESEAU — possède — BASELINE | 1,1 — 1,1 | Un réseau a exactement une baseline. Une baseline appartient à un réseau. |
| RESEAU — génère — ALERT_SESSION | 1,1 — 0,N | Une session vient d'un réseau. Un réseau peut avoir 0 à N sessions dans le temps. |
| ALERT_SESSION — contient — SIGNAL_INSTANCE | 1,1 — 1,N | Un signal appartient à une session. Une session a au moins 1 signal (sinon elle n'existe pas). |

### Note sur SIGNAL_TYPE

`SIGNAL_TYPE` est une **enum dans le code**, pas une entité en base. Les propriétés (poids, standalone, decay) sont des constantes statiques. Seules les **instances concrètes** (ce qui s'est passé réellement) sont persistées en base.

## MLD — Modèle Logique de Données

Traduction du MCD en tables relationnelles.

**Règles de conversion :**
- Chaque entité → une table
- Relation 1,N — 0,N : clé étrangère côté N
- Relation N,N : table de liaison (non applicable ici)

```sql
RESEAU (
  #ssid            TEXT PRIMARY KEY,
   bssid           TEXT NOT NULL,
   gatewayIp       TEXT NOT NULL,
   gatewayMac      TEXT NOT NULL,
   premiereConnexion INTEGER NOT NULL,
   estDeConfiance  INTEGER NOT NULL DEFAULT 0
)

BASELINE (
  #id              TEXT PRIMARY KEY,
   gatewayMac      TEXT NOT NULL,
   gatewayIp       TEXT NOT NULL,
   dnsServeurs     TEXT NOT NULL,   -- JSON array
   bssid           TEXT NOT NULL,
   dateCapture     INTEGER NOT NULL,
   ssid_reseau     TEXT NOT NULL,   -- FK → RESEAU.ssid
   FOREIGN KEY (ssid_reseau) REFERENCES RESEAU(ssid)
)

ALERT_SESSION (
  #id              TEXT PRIMARY KEY,
   networkSsid     TEXT NOT NULL,   -- FK → RESEAU.ssid
   statut          TEXT NOT NULL,   -- OPEN | CLOSED | RESOLVED
   scoreTotal      INTEGER NOT NULL DEFAULT 0,
   niveauFinal     TEXT NOT NULL,   -- SAFE | SUSPECT | WARNING | CRITIQUE
   ouverteA        INTEGER NOT NULL,
   fermetureAuto   INTEGER NOT NULL,
   fermetureEffective INTEGER,
   FOREIGN KEY (networkSsid) REFERENCES RESEAU(ssid)
)

SIGNAL_INSTANCE (
  #id              TEXT PRIMARY KEY,
   sessionId       TEXT NOT NULL,   -- FK → ALERT_SESSION.id
   type            TEXT NOT NULL,   -- nom de l'enum SignalType
   detail          TEXT NOT NULL,
   timestamp       INTEGER NOT NULL,
   expireAt        INTEGER NOT NULL,
   FOREIGN KEY (sessionId) REFERENCES ALERT_SESSION(id)
)
```

---

# 6. Modélisation UML — Diagrammes

## 6.1 Cas d'utilisation

```
┌─────────────────────────────────────────────────────────────────┐
│                         NotMiddlingMe                           │
│                                                                 │
│  ┌──────────┐                                                   │
│  │          │─────── Surveiller réseau en temps réel           │
│  │          │─────── Consulter alertes actives                 │
│  │   USER   │─────── Voir historique des sessions              │
│  │          │─────── Déclencher scan réseau LAN                │
│  │          │─────── Résoudre une alerte                       │
│  │          │─────── Gérer réseaux de confiance                │
│  │          │─────── Exporter rapport                          │
│  └──────────┘                                                   │
│                                                                 │
│  ┌────────────────────┐                                         │
│  │ MonitoringService  │─── Surveiller table ARP                │
│  │ (acteur système)   │─── Scanner réseaux WiFi                │
│  │                    │─── Écouter changements réseau           │
│  └────────────────────┘                                         │
│                                                                 │
│  ┌────────────────────┐                                         │
│  │ VpnService         │─── Intercepter trafic DNS              │
│  │ (acteur système)   │─── Analyser trafic HTTP                │
│  │                    │─── Vérifier certificats TLS            │
│  └────────────────────┘                                         │
└─────────────────────────────────────────────────────────────────┘
```

## 6.2 Diagrammes de Séquence

### Séquence 1 — ARP Spoofing (Détection Passive)

```
MonitoringService  ArpReader    ArpAnalyzer   BaselineRepo   ScoreEngine   NotifManager
       │               │              │              │              │              │
       │──poll()──────►│              │              │              │              │
       │  (toutes 10s) │              │              │              │              │
       │◄──arpTable────│              │              │              │              │
       │  [{ip,mac}]   │              │              │              │              │
       │               │              │              │              │              │
       │──analyze(table)────────────►│              │              │              │
       │               │              │─getBaseline()─────────────►│              │
       │               │              │◄──baseline────────────────-│              │
       │               │              │  {gatewayMac: BB:BB:BB}    │              │
       │               │              │              │              │              │
       │               │              │──compare()   │              │              │
       │               │              │              │              │              │
       │               │       [gateway MAC changé]  │              │              │
       │               │       baseline: BB:BB:BB    │              │              │
       │               │       actuel:  CC:CC:CC     │              │              │
       │               │              │              │              │              │
       │               │              │──addSignal(ARP_GATEWAY_CHANGE, "ENI-WiFi")►│
       │               │              │              │              │              │
       │               │       [doublon MAC détecté] │              │              │
       │               │       192.168.1.1 → CC:CC  │              │              │
       │               │       192.168.1.50→ CC:CC  │              │              │
       │               │              │              │              │              │
       │               │              │──addSignal(ARP_MAC_DUPLICATE, "ENI-WiFi")►│
       │               │              │              │              │  score=10    │
       │               │              │              │              │  →CRITIQUE──►│
       │               │              │              │              │  notify()    │
```

### Séquence 2 — Rogue AP (Détection Passive)

```
MonitoringService  WifiScanner   RogueApAnalyzer  BaselineRepo  ScoreEngine  NotifManager
       │               │                │               │             │             │
       │──scanNetworks()►              │               │             │             │
       │◄──networks[]──│               │               │             │             │
       │  [{ssid,bssid,│               │               │             │             │
       │   security,   │               │               │             │             │
       │   signal}]    │               │               │             │             │
       │               │               │               │             │             │
       │──analyze(networks)───────────►│               │             │             │
       │               │               │─getBaseline()──────────────►             │
       │               │               │◄──baseline─────────────────              │
       │               │               │  {bssid: BB:BB:BB}         │             │
       │               │               │               │             │             │
       │               │               │──findDuplicateSsid()        │             │
       │               │               │               │             │             │
       │               │        ["ENI-WiFi" x2 trouvé] │             │             │
       │               │        bssid1: BB:BB (baseline)│            │             │
       │               │        bssid2: CC:CC (inconnu) │            │             │
       │               │               │               │             │             │
       │               │               │──addSignal(ROGUE_AP_BSSID)──────────────►│
       │               │               │               │             │             │
       │               │        [sécurité dégradée]    │             │             │
       │               │        baseline: WPA2         │             │             │
       │               │        rogue: OPEN            │             │             │
       │               │               │               │             │             │
       │               │               │──addSignal(ROGUE_AP_SECURITY)────────────►│
       │               │               │               │             │  score=8    │
       │               │               │               │             │  →CRITIQUE──►
```

### Séquence 3 — Gateway Monitor (Détection Passive)

```
NetworkCallback   GatewayMonitor    BaselineRepo   ScoreEngine
       │                │                │              │
       │─onLinkChanged()►               │              │
       │  [nouveau LP]  │                │              │
       │                │─extractGateway()              │
       │                │  ip=192.168.1.1               │
       │                │  dns=[192.168.1.50]           │
       │                │                │              │
       │                │─getBaseline()──►              │
       │                │◄──baseline──────              │
       │                │  dns=[8.8.8.8]  ← DNS changé!│
       │                │                │              │
       │                │─compareGateway()              │
       │                │  ip → identique               │
       │                │  dns → CHANGÉ 8.8.8.8→192.168.1.50
       │                │                │              │
       │                │──addSignal(DNS_SERVER_CHANGED, +3)────────►│
       │                │                │              │  score=3   │
       │                │                │              │  → SUSPECT │
```

### Séquence 4 — DNS Spoofing (Détection Active via VpnService)

```
App       VpnService    DnsAnalyzer   TrustedDns(1.1.1.1)  ScoreEngine  NotifManager
 │            │               │               │                 │             │
 │─startVpn()►│               │               │                 │             │
 │            │               │               │                 │             │
 │            │─onPacket()───►│               │                 │             │
 │            │ [UDP port 53] │               │                 │             │
 │            │               │─parseQuery()  │                 │             │
 │            │               │  domain=      │                 │             │
 │            │               │  "fb.com"     │                 │             │
 │            │               │               │                 │             │
 │            │               │─queryTrusted()►                │             │
 │            │               │◄─ip=157.240.x.x               │             │
 │            │               │               │                 │             │
 │            │◄──localResp───│               │                 │             │
 │            │  ip=192.168.1.50              │                 │             │
 │            │               │               │                 │             │
 │            │               │─compare()     │                 │             │
 │            │               │  local=192.168.1.50 [PRIVÉE]   │             │
 │            │               │  trusted=157.240.x.x           │             │
 │            │               │  → DIVERGENCE IP PRIVÉE        │             │
 │            │               │                                 │             │
 │            │               │──addSignal(DNS_PRIVATE_IP, +4)─►             │
 │            │               │                                 │  score=4   │
 │            │               │                                 │  standalone │
 │            │               │                                 │  →CRITIQUE──►
```

### Séquence 5 — SSL Stripping (Détection Active)

```
App       VpnService    SslStripAnalyzer   HstsDatabase   ScoreEngine
 │            │                │                 │              │
 │            │─onPacket()────►│                 │              │
 │            │ [TCP port 80]  │                 │              │
 │            │ host=google.com│                 │              │
 │            │                │─isHstsKnown()──►              │
 │            │                │◄──TRUE──────────              │
 │            │                │   google.com = HTTPS-only     │
 │            │                │                 │              │
 │            │                │─checkProtocol() │              │
 │            │                │  protocol=HTTP  │              │
 │            │                │  domain=HSTS ✅ │              │
 │            │                │  → SSL STRIPPING│              │
 │            │                │                 │              │
 │            │                │──addSignal(SSL_STRIP, +3)─────►│
 │            │                │                 │     score=3  │
 │            │                │                 │     SUSPECT  │
 │            │                │                 │              │
 │            │  [si session ARP déjà ouverte, score = 3+5 = 8] │
 │            │                │                 │     →CRITIQUE│
```

### Séquence 6 — Faux Certificat TLS (Détection Active)

```
App       VpnService    CertAnalyzer    TrustManager    ScoreEngine   NotifManager
 │            │               │               │               │             │
 │            │─onPacket()───►│               │               │             │
 │            │ [TCP port 443]│               │               │             │
 │            │               │─extractCert() │               │             │
 │            │               │               │               │             │
 │            │               │─validate()───►│               │             │
 │            │               │               │─checkCA()     │             │
 │            │               │               │  CA=inconnu   │             │
 │            │               │◄──INVALID_CA──│               │             │
 │            │               │               │               │             │
 │            │               │─isSelfSigned()│               │             │
 │            │               │  → TRUE       │               │             │
 │            │               │               │               │             │
 │            │               │──addSignal(CERT_SELF_SIGNED, +5)────────────►│
 │            │               │──addSignal(CERT_UNKNOWN_CA, +4)─────────────►│
 │            │               │               │               │  score=9    │
 │            │               │               │               │  →CRITIQUE──►
```

### Séquence 7 — DHCP Spoofing (Détection Passive)

```
NetworkCallback    GatewayMonitor     BaselineRepo      ScoreEngine
       │                 │                 │                 │
       │                 │                 │                 │
       │  [CAS 1 — LinkProperties change SANS connexion]     │
       │                 │                 │                 │
       │─onLinkChanged()►│                 │                 │
       │  SANS onAvail() │                 │                 │
       │  [DHCP non soll]│                 │                 │
       │                 │─extractConfig() │                 │
       │                 │  gateway=192.168.1.50             │
       │                 │  dns=[192.168.1.50]               │
       │                 │                 │                 │
       │                 │─getBaseline()──►│                 │
       │                 │◄──baseline──────│                 │
       │                 │                 │                 │
       │                 │──addSignal(GATEWAY_CHANGE_UNSOLICITED, +3)────────────►│
       │                 │                 │                 │                    │
       │                 │─checkDns()      │                 │                    │
       │                 │  dns=192.168.1.50 = IP privée inconnue                │
       │                 │                 │                 │                    │
       │                 │──addSignal(DNS_SERVER_UNKNOWN, +3)────────────────────►│
       │                 │                 │                 │       score=6      │
       │                 │                 │                 │       → SUSPECT    │
       │                 │                 │                 │                    │
       │                 │  [si ARP_MAC_DUPLICATE arrive aussi dans la session]   │
       │                 │  score = 6 + 5 = 11 → CRITIQUE                       │
       │                 │                 │                 │                    │
       │                 │                 │                 │                    │
       │  [CAS 2 — Connexion normale avec gateway différente de baseline]        │
       │                 │                 │                 │                    │
       │─onAvailable()──►│                 │                 │                    │
       │─onLinkChanged()►│                 │                 │                    │
       │  [attendu après │                 │                 │                    │
       │   onAvailable()]│                 │                 │                    │
       │                 │─checkGateway()  │                 │                    │
       │                 │  gateway ≠ baseline               │                    │
       │                 │  (admin a changé le routeur ?)    │                    │
       │                 │                 │                 │                    │
       │                 │──addSignal(GATEWAY_IP_CHANGED, +2)────────────────────►│
       │                 │                 │                 │       score=2      │
       │                 │                 │                 │       → SAFE       │
       │                 │                 │  (signal trop faible seul)          │
```

> **La différence entre les deux cas :** `onAvailable()` avant `onLinkPropertiesChanged()` = connexion normale, changement attendu. `onLinkPropertiesChanged()` sans `onAvailable()` = paquet DHCP non sollicité pendant une session active = suspect.

---

### Séquence 8 — IPv6 Rogue Router Advertisement (Détection Passive)

```
NetworkCallback    IPv6Monitor        BaselineRepo      ScoreEngine
       │                 │                 │                 │
       │─onLinkChanged()►│                 │                 │
       │  [nouveau LP]   │                 │                 │
       │                 │─extractIPv6Routes()               │
       │                 │  gateway6=fe80::dead:beef         │
       │                 │                 │                 │
       │                 │─getBaseline()──►│                 │
       │                 │◄──baseline──────│                 │
       │                 │  gateway6=null  ← réseau IPv4 only│
       │                 │  (ou gateway6 connue différente)  │
       │                 │                 │                 │
       │                 │─checkIPv6Gateway()                │
       │                 │  baseline: pas de gateway IPv6    │
       │                 │  actuel:   fe80::dead:beef        │
       │                 │  → GATEWAY IPv6 INCONNUE          │
       │                 │                 │                 │
       │                 │──addSignal(IPV6_ROGUE_RA, +3)────►│
       │                 │                 │     score=3     │
       │                 │                 │     → SAFE      │
       │                 │                 │     (standalone=false)
       │                 │                 │                 │
       │                 │  [si session ARP/DHCP déjà ouverte]
       │                 │  score = 3 + signaux existants    │
       │                 │  ex: DHCP_GATEWAY(4) + IPV6(3) = 7
       │                 │                 │     → WARNING   │
```

> **Pourquoi standalone=false pour IPv6 RA ?** Parce que beaucoup de réseaux légitimes activent IPv6 avec SLAAC sans que l'admin le configure explicitement. Une gateway IPv6 qui apparaît seule est suspecte mais pas certaine. En combinaison avec d'autres signaux, elle confirme l'attaque.

---

## 6.3 Diagramme de Classes

### Enumerations

```
┌────────────────┐   ┌─────────────────┐   ┌──────────────────────┐
│ SessionStatus  │   │   AlertLevel    │   │     SignalType       │
├────────────────┤   ├─────────────────┤   ├──────────────────────┤
│ OPEN           │   │ SAFE            │   │ ARP_GATEWAY_CHANGE         │
│ CLOSED         │   │ SUSPECT         │   │ ARP_MAC_DUPLICATE          │
│ RESOLVED       │   │ WARNING         │   │ ARP_NEW_ENTRY              │
└────────────────┘   │ CRITIQUE        │   │ GATEWAY_CHANGE_UNSOLICITED │
                     └─────────────────┘   │ GATEWAY_IP_CHANGED         │
                                           │ DNS_SERVER_UNKNOWN         │
                                           │ IPV6_ROGUE_RA              │
                                           │ ROGUE_AP_BSSID             │
                                           │ ROGUE_AP_SECURITY          │
                                           │ ROGUE_AP_SIGNAL            │
                                           │ DNS_PRIVATE_IP             │
                                           │ DNS_SERVER_CHANGED         │
                                           │ TTL_ANORMAL                │
                                           │ SSL_STRIP                  │
                                           │ CERT_SELF_SIGNED           │
                                           │ CERT_UNKNOWN_CA            │
                                           │ CERT_DOMAIN_MISMATCH       │
                                           │ TRAFFIC_MIRROR             │
                                           └────────────────────────────┘
```

### Entities (Data Layer)

```
┌────────────────────────────────┐
│        NetworkBaseline         │
│         <<Entity Room>>        │
├────────────────────────────────┤
│ +ssid: String (PK)             │
│ +bssid: String                 │
│ +gatewayIp: String             │
│ +gatewayMac: String            │
│ +dnsServers: List<String>      │
│ +createdAt: Long               │
│ +isTrusted: Boolean            │
└────────────────────────────────┘

┌────────────────────────────────┐
│         AlertSession           │
│         <<Entity Room>>        │
├────────────────────────────────┤
│ +id: String (PK)               │
│ +networkSsid: String (FK)      │
│ +status: SessionStatus         │
│ +totalScore: Int               │
│ +finalLevel: AlertLevel        │
│ +openedAt: Long                │
│ +autoCloseAt: Long             │
│ +closedAt: Long?               │
└────────────────────────────────┘

┌────────────────────────────────┐
│        SignalInstance          │
│         <<Entity Room>>        │
├────────────────────────────────┤
│ +id: String (PK)               │
│ +sessionId: String (FK)        │
│ +type: SignalType              │
│ +detail: String                │
│ +timestamp: Long               │
│ +expireAt: Long                │
└────────────────────────────────┘
```

### DAOs (Data Layer)

```
┌────────────────────────────────┐
│          BaselineDao           │
│         <<interface>>          │
├────────────────────────────────┤
│ +getBaseline(ssid):            │
│     Flow<NetworkBaseline?>     │
│ +insert(b: NetworkBaseline)    │
│ +update(b: NetworkBaseline)    │
│ +delete(ssid: String)          │
└────────────────────────────────┘

┌────────────────────────────────┐
│           SessionDao           │
│         <<interface>>          │
├────────────────────────────────┤
│ +getOpenSession(ssid):         │
│     Flow<AlertSession?>        │
│ +getAllSessions():             │
│     Flow<List<AlertSession>>   │
│ +insert(s: AlertSession)       │
│ +update(s: AlertSession)       │
└────────────────────────────────┘

┌────────────────────────────────┐
│           SignalDao            │
│         <<interface>>          │
├────────────────────────────────┤
│ +getBySession(id):             │
│     Flow<List<SignalInstance>> │
│ +insert(s: SignalInstance)     │
│ +deleteExpired(now: Long)      │
└────────────────────────────────┘
```

### Repositories (Domain Layer)

```
┌────────────────────────────────┐
│       BaselineRepository       │
├────────────────────────────────┤
│ -dao: BaselineDao              │
├────────────────────────────────┤
│ +getBaseline(ssid):            │
│     Flow<NetworkBaseline?>     │
│ +saveBaseline(b: NetworkBaseline)│
│ +setTrusted(ssid, trusted)     │
└────────────────────────────────┘

┌────────────────────────────────┐
│       SessionRepository        │
├────────────────────────────────┤
│ -sessionDao: SessionDao        │
│ -signalDao: SignalDao          │
├────────────────────────────────┤
│ +getOpenSession(ssid):         │
│     Flow<AlertSession?>        │
│ +getAllSessions():             │
│     Flow<List<AlertSession>>   │
│ +saveSession(s: AlertSession)  │
│ +resolveSession(id: String)    │
│ +addSignalToSession(           │
│     sessionId: String,         │
│     signal: SignalInstance)    │
│ +cleanExpiredSignals()         │
└────────────────────────────────┘
```

### ScoreEngine (Domain Layer — cœur)

```
┌────────────────────────────────────────┐
│              ScoreEngine               │
├────────────────────────────────────────┤
│ -sessionRepo: SessionRepository        │
│ -notifManager: NotificationManager     │
│ -scope: CoroutineScope                 │
├────────────────────────────────────────┤
│ +addSignal(                            │
│     ssid: String,                      │
│     type: SignalType,                  │
│     detail: String                     │
│   )                                    │
│ -getOrCreateSession(ssid):             │
│     AlertSession                       │
│ -recalculateScore(session):            │
│     Int                                │
│ -computeLevel(score, hasStandalone):   │
│     AlertLevel                         │
│ -resetAutoClose(session)               │
│ -notifyIfNeeded(level, oldLevel)       │
└────────────────────────────────────────┘
```

### Analyzers (Domain Layer)

```
┌────────────────────────────┐
│         ArpAnalyzer        │
├────────────────────────────┤
│ -baselineRepo: Baseline... │
│ -scoreEngine: ScoreEngine  │
├────────────────────────────┤
│ +analyze(                  │
│   table: List<ArpEntry>,   │
│   ssid: String             │
│ )                          │
│ -checkGatewayMac()         │
│ -checkMacDuplicates()      │
│ -checkNewEntries()         │
└────────────────────────────┘

┌────────────────────────────┐
│       RogueApAnalyzer      │
├────────────────────────────┤
│ -baselineRepo: Baseline... │
│ -scoreEngine: ScoreEngine  │
├────────────────────────────┤
│ +analyze(                  │
│   networks: List<WifiAp>,  │
│   ssid: String             │
│ )                          │
│ -checkDuplicateSsid()      │
│ -checkSecurityDowngrade()  │
│ -checkSignalStrength()     │
└────────────────────────────┘

┌────────────────────────────┐
│       GatewayMonitor       │
├────────────────────────────┤
│ -baselineRepo: Baseline... │
│ -scoreEngine: ScoreEngine  │
├────────────────────────────┤
│ +onNetworkChanged(         │
│   lp: LinkProperties,      │
│   ssid: String             │
│ )                          │
│ -checkDnsServers()         │
│ -checkGatewayIp()          │
└────────────────────────────┘

┌────────────────────────────┐
│         DnsAnalyzer        │
├────────────────────────────┤
│ -scoreEngine: ScoreEngine  │
│ -trustedDns: String        │
├────────────────────────────┤
│ +analyze(                  │
│   packet: DnsPacket,       │
│   ssid: String             │
│ )                          │
│ -isPrivateIp(ip): Boolean  │
│ -queryTrustedDns(domain)   │
│ -checkTtl(ttl: Int)        │
└────────────────────────────┘

┌────────────────────────────┐
│        CertAnalyzer        │
├────────────────────────────┤
│ -scoreEngine: ScoreEngine  │
├────────────────────────────┤
│ +analyze(                  │
│   cert: X509Certificate,   │
│   domain: String,          │
│   ssid: String             │
│ )                          │
│ -isSelfSigned(): Boolean   │
│ -isKnownCa(): Boolean      │
│ -domainMatches(): Boolean  │
└────────────────────────────┘

┌────────────────────────────┐
│      SslStripAnalyzer      │
├────────────────────────────┤
│ -scoreEngine: ScoreEngine  │
│ -hstsDb: HstsDatabase      │
├────────────────────────────┤
│ +analyze(                  │
│   packet: HttpPacket,      │
│   ssid: String             │
│ )                          │
│ -isHstsDomain(host):Boolean│
└────────────────────────────┘
```

### Services (Service Layer)

```
┌────────────────────────────────────────┐
│          MonitoringService             │
│         <<ForegroundService>>          │
├────────────────────────────────────────┤
│ -arpAnalyzer: ArpAnalyzer              │
│ -rogueApAnalyzer: RogueApAnalyzer      │
│ -gatewayMonitor: GatewayMonitor        │
│ -scope: CoroutineScope                 │
├────────────────────────────────────────┤
│ +onStartCommand(): Int                 │
│ +onDestroy()                           │
│ -startArpPolling()                     │
│ -startWifiScanning()                   │
│ -registerNetworkCallback()             │
│ -showPersistentNotification()          │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│        NetSentinelVpnService           │
│           <<VpnService>>               │
├────────────────────────────────────────┤
│ -dnsAnalyzer: DnsAnalyzer              │
│ -certAnalyzer: CertAnalyzer            │
│ -sslStripAnalyzer: SslStripAnalyzer    │
│ -tunInterface: FileDescriptor?         │
│ -scope: CoroutineScope                 │
├────────────────────────────────────────┤
│ +onStartCommand(): Int                 │
│ +onRevoke()                            │
│ -buildTunnel(): FileDescriptor         │
│ -processPackets()                      │
│ -forwardPacket(packet: ByteArray)      │
│ -parseDnsPacket(data): DnsPacket?      │
│ -extractCertificate(data): X509Cert?   │
└────────────────────────────────────────┘
```

### ViewModels (ViewModel Layer)

```
┌────────────────────────────────────────┐
│          DashboardViewModel            │
├────────────────────────────────────────┤
│ -sessionRepo: SessionRepository        │
│ -baselineRepo: BaselineRepository      │
│ -wifiManager: WifiManager              │
├────────────────────────────────────────┤
│ +currentSession:                       │
│     StateFlow<AlertSession?>           │
│ +networkInfo:                          │
│     StateFlow<NetworkInfo>             │
│ +activeSignals:                        │
│     StateFlow<List<SignalInstance>>    │
│ +resolveAlert(sessionId: String)       │
│ +toggleVpn()                           │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│           AlertsViewModel              │
├────────────────────────────────────────┤
│ -sessionRepo: SessionRepository        │
├────────────────────────────────────────┤
│ +history:                              │
│     StateFlow<List<AlertSession>>      │
│ +signalsFor(id: String):               │
│     StateFlow<List<SignalInstance>>    │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│           ScannerViewModel             │
├────────────────────────────────────────┤
│ -wifiManager: WifiManager              │
│ -arpReader: ArpReader                  │
├────────────────────────────────────────┤
│ +devices:                              │
│     StateFlow<List<LanDevice>>         │
│ +isScanning: StateFlow<Boolean>        │
│ +startScan()                           │
└────────────────────────────────────────┘
```

### Dépendances globales

```
UI Composables
  └──► DashboardViewModel ──► SessionRepository ──► SessionDao
  └──► AlertsViewModel    ──► SessionRepository ──► SignalDao
  └──► ScannerViewModel   ──► WifiManager, ArpReader
                               ▲
                               │
MonitoringService ──► ArpAnalyzer ─────────────┐
                  ──► RogueApAnalyzer ──────────┤──► ScoreEngine ──► SessionRepository
                  ──► GatewayMonitor ───────────┘        │
                                                         └──► NotificationManager
VpnService ───────► DnsAnalyzer ───────────────┐
           ───────► CertAnalyzer ──────────────┤──► ScoreEngine
           ───────► SslStripAnalyzer ──────────┘
```

**Règle d'architecture fondamentale :**
> Les Analyzers ne savent pas qu'ils sont appelés depuis un Service. Ils prennent des données en entrée, produisent des signaux. Le Service orchestre, l'Analyzer analyse. Séparation stricte des responsabilités.

---

# 7. UI/UX — Wireframes & Design System

## User Flow

```
┌─────────────────┐
│   Lancement     │
└────────┬────────┘
         │
         ▼
    première fois ?
         │
    OUI  │  NON
         │   └──────────────────────┐
         ▼                          ▼
┌─────────────────┐        ┌─────────────────┐
│   Onboarding    │        │   Dashboard     │
│ Demande perms   │───────►│ (écran principal│
│ WiFi/Notifs/VPN │        └────────┬────────┘
└─────────────────┘                 │
                          ┌─────────┼──────────────────┐
                          │         │                  │
                          ▼         ▼                  ▼
                   ┌──────────┐ ┌──────────┐  ┌──────────────┐
                   │ Tap sur  │ │  Scan    │  │  BottomNav   │
                   │ alerte   │ │ réseau   │  │  Historique  │
                   └────┬─────┘ └──────────┘  │  Paramètres  │
                        │                     └──────────────┘
                        ▼
                 ┌──────────────────┐
                 │ Alerte détaillée │
                 │ Timeline signaux │
                 │ Conseils         │
                 │ Résoudre         │
                 └──────────────────┘
```

## Design System

### Palette de couleurs

```
Couleurs sémantiques (état réseau) :
  Safe     →  #2ECC71  Vert
  Suspect  →  #F39C12  Orange
  Warning  →  #E67E22  Orange foncé
  Critique →  #E74C3C  Rouge

Couleurs de fond (dark theme) :
  Background   →  #0F1923  Bleu nuit très sombre
  Surface      →  #1A2634  Bleu nuit
  SurfaceVar   →  #243447  Bleu ardoise
  OnSurface    →  #ECEFF1  Blanc cassé
  OnSurfaceLow →  #ECEFF199 60% opacity

Pourquoi dark theme ?
  → App utilisée en situation de stress ou la nuit
  → Less fatiguant visuellement
  → Esthétique "sécurité" crédible
```

### Typographie

```
Score principal  →  72sp, Black, couleur sémantique
Titre écran      →  24sp, Bold, OnSurface
Titre section    →  18sp, SemiBold, OnSurface
Corps texte      →  14sp, Regular, OnSurface
Caption / détail →  12sp, Regular, OnSurfaceLow
Label chip       →  11sp, Medium, uppercase
```

### Espacements

```
xs  →  4dp   (écart interne minime)
sm  →  8dp   (padding interne card)
md  →  16dp  (padding standard écran)
lg  →  24dp  (séparation sections)
xl  →  32dp  (grand espacement)
xxl →  48dp  (espacement héroïque)
```

### Composants réutilisables

```
AlertLevelBadge   → chip colorée selon AlertLevel
SignalCard        → carte d'un signal avec icône, titre, score, timestamp
ScoreGauge        → cercle central avec score et niveau
NetworkInfoRow    → ligne IP/MAC/BSSID avec indicateur suspect
DeviceListItem    → item liste LAN scan avec OUI constructeur
SectionHeader     → en-tête de section avec séparateur
```

## Wireframes

### Écran 1 — Dashboard (état SAFE)

```
┌─────────────────────────────────┐
│  NotMiddlingMe           ⚙️    │
├─────────────────────────────────┤
│                                 │
│      ╔═══════════════╗          │
│      ║               ║          │
│      ║      85       ║          │
│      ║   SÉCURISÉ 🟢 ║          │
│      ║               ║          │
│      ╚═══════════════╝          │
│                                 │
├─────────────────────────────────┤
│  📶  ENI-WiFi                   │
│  Gateway    192.168.1.1         │
│  DNS        8.8.8.8             │
│  BSSID      AA:BB:CC:DD:EE:FF   │
├─────────────────────────────────┤
│  Protection active              │
│  Passif ✅    Actif (VPN) ✅    │
│                    [  ON  ] ◀── │
├─────────────────────────────────┤
│  Aucune alerte aujourd'hui 🟢   │
│                                 │
│      [ 🔍 Scanner le réseau ]   │
│                                 │
└──────┬──────────┬───────────────┘
    [🏠 Home] [📋 Hist.] [⚙️ Params]
```

### Écran 1b — Dashboard (état CRITIQUE)

```
┌─────────────────────────────────┐
│  NotMiddlingMe           ⚙️    │
├─────────────────────────────────┤
│                                 │
│      ╔═══════════════╗          │
│      ║               ║          │  ← fond rouge pulsant
│      ║      14       ║          │
│      ║  ⚠️ ATTAQUE  ║          │
│      ║               ║          │
│      ╚═══════════════╝          │
│                                 │
├─────────────────────────────────┤
│  📶  ENI-WiFi                   │
│  Gateway    192.168.1.1  ⚠️    │  ← indicateur sur ligne suspecte
│  DNS        192.168.1.50 🔴    │
│  BSSID      AA:BB:CC:DD:EE:FF   │
├─────────────────────────────────┤
│  🔴 ARP Spoofing détecté        │
│  🔴 DNS Server changé           │  ← résumé signaux actifs
│  🟡 TTL anormal                 │
├─────────────────────────────────┤
│  [ Voir les détails ]           │
│  [ Couper le réseau WiFi ]      │
└─────────────────────────────────┘
```

> En état d'alerte : supprimer le bruit. L'utilisateur voit le problème, pas le dashboard complet.

### Écran 2 — Alerte Détaillée

```
┌─────────────────────────────────┐
│  ← Alerte #1234                 │
├─────────────────────────────────┤
│                                 │
│  🔴 CRITIQUE — Score 14         │
│  ENI-WiFi · il y a 3 min        │
│                                 │
├─────────────────────────────────┤
│  SIGNAUX DÉTECTÉS               │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🔴 ARP Gateway Change    │  │
│  │ MAC Gateway modifiée      │  │
│  │ BB:BB:BB → CC:CC:CC       │  │
│  │ 14:32:01  ·  +5 pts       │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🔴 DNS Private IP        │  │
│  │ facebook.com → 192.168.x  │  │
│  │ 14:32:03  ·  +4 pts       │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🟡 TTL Anormal           │  │
│  │ TTL=0 sur requête DNS     │  │
│  │ 14:32:05  ·  +1 pt        │  │
│  └───────────────────────────┘  │
│                                 │
├─────────────────────────────────┤
│  QUE FAIRE ?                    │
│  → Ne vous connectez pas à des  │
│    services sensibles           │
│  → Passez sur données mobiles   │
├─────────────────────────────────┤
│  [ ✅ Marquer comme résolu ]    │
│  [ 📤 Exporter le rapport ]     │
└─────────────────────────────────┘
```

### Écran 3 — Historique

```
┌─────────────────────────────────┐
│  Historique                     │
├─────────────────────────────────┤
│  [Tous] [🔴 Critique] [🟡 Susp]│
├─────────────────────────────────┤
│                                 │
│  Aujourd'hui                    │
│  ┌───────────────────────────┐  │
│  │ 🔴 14:32  ENI-WiFi       │► │
│  │ ARP Spoofing · CRITIQUE   │  │
│  │ Score 14 · Résolu ✅      │  │
│  └───────────────────────────┘  │
│                                 │
│  Hier                           │
│  ┌───────────────────────────┐  │
│  │ 🟡 09:15  Maison-WiFi    │► │
│  │ DNS Server changé         │  │
│  │ Score 3 · Fermé           │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🟢 08:00  Maison-WiFi    │► │
│  │ Aucune anomalie           │  │
│  │ Score 0                   │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

### Écran 4 — Scanner Réseau

```
┌─────────────────────────────────┐
│  ← Scanner réseau               │
├─────────────────────────────────┤
│  ENI-WiFi · 192.168.1.0/24      │
│                                 │
│      [ 🔍 Lancer le scan ]      │
│                                 │
├─────────────────────────────────┤
│  12 appareils trouvés           │
│                                 │
│  IP             MAC       Info  │
│  ─────────────────────────────  │
│  192.168.1.1   AA:BB:CC   Xiaomi Router  │
│  192.168.1.5   11:22:33   Samsung       │
│  192.168.1.42  44:55:66   [Vous] ✅     │
│  192.168.1.99  77:88:99   Inconnu ⚠️   │
│                                 │
│  ┌───────────────────────────┐  │
│  │ ⚠️ 192.168.1.99          │  │
│  │ Constructeur inconnu      │  │
│  │ Apparu il y a 5 min       │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

### Écran 5 — Paramètres

```
┌─────────────────────────────────┐
│  Paramètres                     │
├─────────────────────────────────┤
│  RÉSEAUX DE CONFIANCE           │
│  ┌───────────────────────────┐  │
│  │ ✅ Maison-WiFi         ×  │  │
│  │ ✅ Bureau-WiFi         ×  │  │
│  └───────────────────────────┘  │
│  [ + Ajouter réseau actuel ]    │
│                                 │
├─────────────────────────────────┤
│  DÉTECTION                      │
│  Intervalle scan ARP    [10s ▼] │
│  DNS de confiance   [1.1.1.1  ] │
│  VPN auto réseau public  [ON ]  │
│                                 │
├─────────────────────────────────┤
│  ALERTES                        │
│  Notifications           [ON ]  │
│  Vibration CRITIQUE      [ON ]  │
│  Son d'alerte            [OFF]  │
│                                 │
├─────────────────────────────────┤
│  [ 📤 Exporter tout l'historique]│
│  [ 🗑️ Effacer l'historique    ] │
└─────────────────────────────────┘
```

## Navigation Compose

```kotlin
sealed class Screen(val route: String) {
    object Dashboard    : Screen("dashboard")
    object AlertDetail  : Screen("alert/{sessionId}") {
        fun withId(id: String) = "alert/$id"
    }
    object History      : Screen("history")
    object Scanner      : Screen("scanner")
    object Settings     : Screen("settings")
    object Onboarding   : Screen("onboarding")
}

// Structure de navigation :
// NavHost
// ├── Onboarding (si première ouverture)
// └── MainScaffold (BottomNavigationBar)
//     ├── Dashboard → AlertDetail
//     ├── History   → AlertDetail
//     └── Settings

// Dashboard → Scanner (pas dans BottomNav, accessible via bouton)
```

---

# 8. Architecture Technique

## Stack technologique

```
Langage         →  Kotlin
UI              →  Jetpack Compose
Navigation      →  Navigation Compose
DI              →  Hilt
Async           →  Coroutines + Flow
Base de données →  Room
Background      →  ForegroundService + WorkManager
VPN             →  VpnService Android
Réseau          →  OkHttp (pour queries DNS de confiance)
Build           →  Gradle KTS
Min SDK         →  API 26 (Android 8.0)
Target SDK      →  API 34
```

## Structure du projet

```
app/
├── di/
│   └── AppModule.kt               ← injection Hilt
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── BaselineDao.kt
│   │   ├── SessionDao.kt
│   │   └── SignalDao.kt
│   ├── model/
│   │   ├── NetworkBaseline.kt     ← Entity Room
│   │   ├── AlertSession.kt        ← Entity Room
│   │   └── SignalInstance.kt      ← Entity Room
│   ├── repository/
│   │   ├── BaselineRepository.kt
│   │   └── SessionRepository.kt
│   └── source/
│       ├── ArpReader.kt           ← lit /proc/net/arp
│       └── WifiScanner.kt         ← WifiManager wrapper
│
├── domain/
│   ├── model/
│   │   ├── AlertLevel.kt          ← enum
│   │   ├── SessionStatus.kt       ← enum
│   │   ├── SignalType.kt          ← enum avec poids/decay
│   │   ├── ArpEntry.kt
│   │   ├── WifiAp.kt
│   │   ├── LanDevice.kt
│   │   ├── DnsPacket.kt
│   │   └── NetworkInfo.kt
│   ├── engine/
│   │   └── ScoreEngine.kt         ← cœur du système
│   └── analyzer/
│       ├── ArpAnalyzer.kt
│       ├── RogueApAnalyzer.kt
│       ├── GatewayMonitor.kt
│       ├── DnsAnalyzer.kt
│       ├── CertAnalyzer.kt
│       └── SslStripAnalyzer.kt
│
├── service/
│   ├── MonitoringService.kt       ← ForegroundService (détection passive)
│   └── NetSentinelVpnService.kt   ← VpnService (détection active)
│
└── ui/
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   └── Theme.kt
    ├── component/
    │   ├── AlertLevelBadge.kt
    │   ├── SignalCard.kt
    │   ├── ScoreGauge.kt
    │   └── NetworkInfoRow.kt
    ├── screen/
    │   ├── onboarding/
    │   │   └── OnboardingScreen.kt
    │   ├── dashboard/
    │   │   ├── DashboardScreen.kt
    │   │   └── DashboardViewModel.kt
    │   ├── alert/
    │   │   ├── AlertDetailScreen.kt
    │   │   └── AlertDetailViewModel.kt
    │   ├── history/
    │   │   ├── HistoryScreen.kt
    │   │   └── HistoryViewModel.kt
    │   ├── scanner/
    │   │   ├── ScannerScreen.kt
    │   │   └── ScannerViewModel.kt
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── SettingsViewModel.kt
    └── MainActivity.kt
```

## AndroidManifest — permissions

```xml
<manifest>

    <!-- Réseau -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- VPN -->
    <uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

    <application ...>

        <!-- ForegroundService de monitoring -->
        <service
            android:name=".service.MonitoringService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />

        <!-- VpnService -->
        <service
            android:name=".service.NetSentinelVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

---

# 9. Guide d'Implémentation

## Ordre d'implémentation recommandé

```
Phase 1 — Fondations (sans détection)
  1. Projet Android + Hilt + Room + Compose Navigation
  2. Entités Room + DAOs + Repositories
  3. Lecture /proc/net/arp (ArpReader)
  4. WifiManager wrapper
  5. MonitoringService (ForegroundService vide)

Phase 2 — Détection passive
  6. ScoreEngine (addSignal, scoring, decay)
  7. ArpAnalyzer + baseline
  8. RogueApAnalyzer
  9. GatewayMonitor
  10. NotificationManager (3 niveaux)

Phase 3 — UI
  11. Dashboard (score + infos réseau)
  12. Alerte détaillée (timeline signaux)
  13. Historique
  14. Paramètres (réseaux de confiance)

Phase 4 — Détection active
  15. VpnService de base (tunnel TUN fonctionnel)
  16. DnsAnalyzer (parser UDP/53)
  17. SslStripAnalyzer (HTTP vers domaine HSTS)
  18. CertAnalyzer (TLS/443)

Phase 5 — Polish
  19. Scanner réseau LAN
  20. Export rapport JSON/PDF
  21. Onboarding
```

## Implémentation — Extraits clés

### ArpEntry & ArpReader

```kotlin
data class ArpEntry(
    val ip: String,
    val mac: String,
    val device: String  // interface réseau ex: wlan0
)

class ArpReader @Inject constructor() {

    fun readArpTable(): List<ArpEntry> {
        val entries = mutableListOf<ArpEntry>()
        try {
            File("/proc/net/arp").forEachLine { line ->
                // format : IP HWtype Flags HWaddress Mask Device
                // ex    : 192.168.1.1 0x1 0x2 aa:bb:cc:dd:ee:ff * wlan0
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 6 && parts[0] != "IP") {
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00") { // entrées invalides
                        entries.add(ArpEntry(
                            ip = parts[0],
                            mac = mac.uppercase(),
                            device = parts[5]
                        ))
                    }
                }
            }
        } catch (e: IOException) {
            // /proc/net/arp non lisible → log silencieux
        }
        return entries
    }
}
```

### SignalType enum

```kotlin
enum class SignalType(
    val poids: Int,
    val standalone: Boolean,
    val decaySeconds: Int,
    val description: String
) {
    ARP_GATEWAY_CHANGE(
        poids = 5,
        standalone = true,
        decaySeconds = 60,
        description = "MAC de la gateway modifiée"
    ),
    ARP_MAC_DUPLICATE(
        poids = 5,
        standalone = true,
        decaySeconds = 60,
        description = "Même MAC pour plusieurs IPs"
    ),
    ARP_NEW_ENTRY(
        poids = 1,
        standalone = false,
        decaySeconds = 30,
        description = "Nouvelle entrée ARP inattendue"
    ),
    ROGUE_AP_BSSID(
        poids = 4,
        standalone = true,
        decaySeconds = 120,
        description = "BSSID différent pour même SSID"
    ),
    ROGUE_AP_SECURITY(
        poids = 4,
        standalone = true,
        decaySeconds = 120,
        description = "Sécurité dégradée sur même SSID"
    ),
    ROGUE_AP_SIGNAL(
        poids = 1,
        standalone = false,
        decaySeconds = 60,
        description = "Signal anormalement fort"
    ),
    DNS_PRIVATE_IP(
        poids = 4,
        standalone = true,
        decaySeconds = 30,
        description = "Domaine public résolu en IP privée"
    ),
    DNS_SERVER_CHANGED(
        poids = 3,
        standalone = false,
        decaySeconds = 60,
        description = "Serveur DNS modifié"
    ),
    TTL_ANORMAL(
        poids = 1,
        standalone = false,
        decaySeconds = 10,
        description = "TTL DNS anormalement bas"
    ),
    SSL_STRIP(
        poids = 3,
        standalone = false,
        decaySeconds = 20,
        description = "HTTP vers domaine HTTPS-only"
    ),
    CERT_SELF_SIGNED(
        poids = 5,
        standalone = true,
        decaySeconds = 30,
        description = "Certificat auto-signé détecté"
    ),
    CERT_UNKNOWN_CA(
        poids = 4,
        standalone = true,
        decaySeconds = 30,
        description = "CA inconnue ou non standard"
    ),
    CERT_DOMAIN_MISMATCH(
        poids = 5,
        standalone = true,
        decaySeconds = 30,
        description = "Le domaine ne correspond pas au certificat"
    ),
    TRAFFIC_MIRROR(
        poids = 4,
        standalone = true,
        decaySeconds = 60,
        description = "Connexions dupliquées détectées"
    )
}
```

### ScoreEngine

```kotlin
@Singleton
class ScoreEngine @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val notifManager: AlertNotificationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    fun addSignal(ssid: String, type: SignalType, detail: String) {
        scope.launch {
            val now = System.currentTimeMillis()

            // 1. Récupérer ou créer la session ouverte pour ce réseau
            val session = getOrCreateSession(ssid, now)

            // 2. Créer l'instance de signal
            val signal = SignalInstance(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                type = type,
                detail = detail,
                timestamp = now,
                expireAt = now + (type.decaySeconds * 1000L)
            )

            // 3. Persister le signal
            sessionRepo.addSignalToSession(session.id, signal)

            // 4. Recalculer le score (signaux non expirés uniquement)
            val activeSignals = sessionRepo.getActiveSignals(session.id, now)
            val newScore = activeSignals.sumOf { it.type.poids }
            val hasStandalone = activeSignals.any { it.type.standalone }

            // 5. Calculer le niveau
            val newLevel = computeLevel(newScore, hasStandalone)
            val oldLevel = session.finalLevel

            // 6. Reset fenêtre glissante
            val newAutoClose = now + 30_000L // 30 secondes

            // 7. Mettre à jour la session
            sessionRepo.saveSession(session.copy(
                totalScore = newScore,
                finalLevel = newLevel,
                autoCloseAt = newAutoClose
            ))

            // 8. Notifier si niveau a changé ou empiré
            if (newLevel > oldLevel) {
                notifManager.notify(session.id, newLevel, type.description)
            }
        }
    }

    private suspend fun getOrCreateSession(ssid: String, now: Long): AlertSession {
        val existing = sessionRepo.getOpenSessionSync(ssid)
        if (existing != null) return existing

        val session = AlertSession(
            id = UUID.randomUUID().toString(),
            networkSsid = ssid,
            status = SessionStatus.OPEN,
            totalScore = 0,
            finalLevel = AlertLevel.SAFE,
            openedAt = now,
            autoCloseAt = now + 30_000L,
            closedAt = null
        )
        sessionRepo.saveSession(session)
        return session
    }

    private fun computeLevel(score: Int, hasStandalone: Boolean): AlertLevel {
        if (hasStandalone && score >= 4) return AlertLevel.CRITIQUE
        return when (score) {
            in 0..3   -> AlertLevel.SAFE
            in 4..6   -> AlertLevel.SUSPECT
            in 7..9   -> AlertLevel.WARNING
            else      -> AlertLevel.CRITIQUE
        }
    }
}
```

### ArpAnalyzer

```kotlin
@Singleton
class ArpAnalyzer @Inject constructor(
    private val baselineRepo: BaselineRepository,
    private val scoreEngine: ScoreEngine
) {

    suspend fun analyze(table: List<ArpEntry>, ssid: String) {
        val baseline = baselineRepo.getBaselineSync(ssid) ?: return

        checkGatewayMac(table, baseline, ssid)
        checkMacDuplicates(table, ssid)
    }

    private fun checkGatewayMac(
        table: List<ArpEntry>,
        baseline: NetworkBaseline,
        ssid: String
    ) {
        val currentGatewayEntry = table.find { it.ip == baseline.gatewayIp }
            ?: return

        if (currentGatewayEntry.mac != baseline.gatewayMac) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.ARP_GATEWAY_CHANGE,
                detail = "Gateway ${baseline.gatewayIp}: " +
                         "${baseline.gatewayMac} → ${currentGatewayEntry.mac}"
            )
        }
    }

    private fun checkMacDuplicates(table: List<ArpEntry>, ssid: String) {
        val macToIps = table.groupBy { it.mac }

        macToIps.forEach { (mac, entries) ->
            if (entries.size > 1) {
                val ips = entries.joinToString(", ") { it.ip }
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.ARP_MAC_DUPLICATE,
                    detail = "MAC $mac répond pour : $ips"
                )
            }
        }
    }
}
```

### DnsAnalyzer — vérification IP privée

```kotlin
@Singleton
class DnsAnalyzer @Inject constructor(
    private val scoreEngine: ScoreEngine
) {
    private val trustedDns = "1.1.1.1"
    private val privateRanges = listOf(
        Regex("^10\\..*"),
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"),
        Regex("^192\\.168\\..*"),
        Regex("^127\\..*"),
        Regex("^169\\.254\\..*")
    )

    fun analyze(resolvedIp: String, domain: String, ssid: String) {
        // Règle 1 — IP privée pour domaine public → TOUJOURS suspect
        if (isPrivateIp(resolvedIp)) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.DNS_PRIVATE_IP,
                detail = "$domain résolu en IP privée: $resolvedIp"
            )
            return
        }

        // Règle 2 — comparer avec DNS de confiance (async)
        // Note : ne pas bloquer le thread de capture paquets
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val trustedIp = queryDns(domain, trustedDns)
                // On vérifie l'ASN, pas l'IP exacte (load balancing)
                // Pour V1 : on vérifie juste si même /16
                if (!sameSubnet16(resolvedIp, trustedIp)) {
                    scoreEngine.addSignal(
                        ssid = ssid,
                        type = SignalType.DNS_PRIVATE_IP,
                        detail = "$domain: local=$resolvedIp trusted=$trustedIp"
                    )
                }
            } catch (e: Exception) {
                // DNS de confiance injoignable → pas de signal (réseau peut être coupé)
            }
        }
    }

    private fun isPrivateIp(ip: String) = privateRanges.any { it.matches(ip) }

    private fun sameSubnet16(ip1: String, ip2: String): Boolean {
        val parts1 = ip1.split(".")
        val parts2 = ip2.split(".")
        if (parts1.size < 2 || parts2.size < 2) return false
        return parts1[0] == parts2[0] && parts1[1] == parts2[1]
    }
}
```

### MonitoringService

```kotlin
@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var arpReader: ArpReader
    @Inject lateinit var arpAnalyzer: ArpAnalyzer
    @Inject lateinit var rogueApAnalyzer: RogueApAnalyzer
    @Inject lateinit var gatewayMonitor: GatewayMonitor
    @Inject lateinit var baselineRepo: BaselineRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showPersistentNotification()
        startArpPolling()
        startWifiScanning()
        registerNetworkCallback()
        return START_STICKY
    }

    private fun startArpPolling() {
        scope.launch {
            while (isActive) {
                val ssid = getCurrentSsid() ?: continue
                val table = arpReader.readArpTable()

                // Sauvegarder baseline si première connexion
                ensureBaseline(ssid, table)

                // Analyser
                arpAnalyzer.analyze(table, ssid)

                delay(10_000L) // toutes les 10 secondes
            }
        }
    }

    private fun startWifiScanning() {
        scope.launch {
            while (isActive) {
                val ssid = getCurrentSsid() ?: continue
                val networks = scanWifiNetworks()
                rogueApAnalyzer.analyze(networks, ssid)
                delay(30_000L) // toutes les 30 secondes
            }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                val ssid = getCurrentSsid() ?: return
                scope.launch {
                    gatewayMonitor.onNetworkChanged(linkProperties, ssid)
                }
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    private fun showPersistentNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotMiddlingMe")
            .setContentText("Protection active")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
```

### ScoreGauge Composable

```kotlin
@Composable
fun ScoreGauge(
    score: Int,
    level: AlertLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        AlertLevel.SAFE     -> Color(0xFF2ECC71)
        AlertLevel.SUSPECT  -> Color(0xFFF39C12)
        AlertLevel.WARNING  -> Color(0xFFE67E22)
        AlertLevel.CRITIQUE -> Color(0xFFE74C3C)
    }

    val label = when (level) {
        AlertLevel.SAFE     -> "SÉCURISÉ"
        AlertLevel.SUSPECT  -> "SUSPECT"
        AlertLevel.WARNING  -> "ATTENTION"
        AlertLevel.CRITIQUE -> "ATTAQUE"
    }

    // Animation pulsante si CRITIQUE
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (level == AlertLevel.CRITIQUE) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha * 0.15f))
            .border(3.dp, color.copy(alpha = alpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = color.copy(alpha = alpha)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = alpha),
                letterSpacing = 2.sp
            )
        }
    }
}
```

---

# 10. Limites & Faux Positifs

## Ce que l'app détecte fiablement

```
✅ Changement de MAC gateway sur LAN
   → Quasi-impossible légitimement sans intervention manuelle

✅ Même MAC pour plusieurs IPs sur le réseau
   → Signature classique de l'ARP Spoofing, très peu d'ambiguïté

✅ Gateway IP ou DNS changent via paquet DHCP non sollicité
   → Détecté via LinkProperties sans onAvailable() préalable
   → Corrélé avec ARP pour confirmation (GATEWAY_CHANGE_UNSOLICITED + ARP_MAC_DUPLICATE)

✅ Rogue AP (même SSID, BSSID différent)
   → Indique un point d'accès non légitime imitant le réseau

✅ Certificat TLS self-signed sur domaine public
   → Impossible légitimement pour un service public

✅ CA inconnue dans le certificat TLS
   → Proxy MITM probable (Burp, mitmproxy, etc.)

✅ IP privée retournée par DNS pour domaine public
   → DNS Spoofing quasi-certain
```

## Ce que l'app détecte avec possibilité de faux positifs

```
⚠️ Divergence DNS entre serveur local et 1.1.1.1
   Cause légitime : load balancing, géo-routing (Google, Cloudflare)
   Mitigation : comparer /16 et non IP exacte, vérifier si IP privée

⚠️ Changement de BSSID sur même SSID
   Cause légitime : réseau avec plusieurs points d'accès (campus, entreprise)
   Mitigation : croiser avec force signal et sécurité

⚠️ Gateway IPv6 inconnue (IPv6 Rogue RA)
   Cause légitime : réseau avec SLAAC activé par défaut
   Mitigation : signal faible (+3), standalone=false, pertinent seulement en corrélation

⚠️ TTL DNS bas
   Cause légitime : CDN aggressif (Cloudflare, Fastly)
   Mitigation : signal faible (+1), jamais standalone

⚠️ Nouveau device ARP inattendu
   Cause légitime : invité qui rejoint le réseau
   Mitigation : signal faible (+1), jamais standalone
```

## Ce que l'app ne détecte PAS

```
❌ Trafic des autres appareils du réseau
   Raison : impossible sans root sur Android

❌ MITM sur réseau 4G/5G
   Raison : pas d'accès au LAN cellulaire

❌ Attaquant avec accès physique au routeur
   Raison : modifications au niveau firmware, invisible depuis le client

❌ MITM sur protocoles exotiques non-HTTP/DNS
   Raison : parsing limité aux protocoles communs

❌ Attaque si VPN externe actif
   Raison : le trafic sort déjà chiffré vers le VPN, la détection active perd sa visibilité
```

## Ce qu'il faut dire en soutenance

> *"NotMiddlingMe est un système de détection d'anomalies réseau côté client. Il ne prétend pas détecter 100% des attaques MITM — ce serait techniquement faux. Il détecte les signatures connues et observables depuis un appareil Android sans root, avec un modèle de scoring qui minimise les faux positifs par corrélation multi-signaux."*

Lister ses limites en soutenance = maîtrise du sujet.  
Les cacher = incompétence visible.

---

## Résumé final — ce que tu as construit

```
THÉORIE
✅ Modèle OSI, IP/MAC/Port, TCP/UDP
✅ HTTP/HTTPS/TLS et ses attaques
✅ ARP Spoofing, DHCP Spoofing, IPv6 Rogue RA, DNS Spoofing, SSL Stripping, Faux certificat
✅ Contraintes Android (VpnService, /proc/net/arp, WifiManager)

MODÉLISATION
✅ Threat model formalisé (7 vecteurs, préconditions, effets)
✅ Modèle de détection (scoring, decay, AlertSession, fenêtre glissante)
✅ MCD Merise (4 entités, cardinalités justifiées)
✅ MLD Merise (4 tables SQL, clés étrangères)
✅ Diagramme de cas d'utilisation UML
✅ 8 diagrammes de séquence UML (tous les vecteurs d'attaque)
✅ Diagramme de classes UML (toutes les couches)

UI/UX
✅ User flow complet
✅ Design system (couleurs, typo, espacements)
✅ 5 wireframes annotés
✅ Architecture de navigation Compose

IMPLÉMENTATION
✅ Stack technique justifiée
✅ Structure projet détaillée
✅ AndroidManifest avec permissions
✅ Extraits de code clés (ArpReader, ScoreEngine, ArpAnalyzer, DnsAnalyzer, MonitoringService, ScoreGauge)
✅ Ordre d'implémentation par phases

LIMITES
✅ Faux positifs documentés avec mitigations
✅ Périmètre de détection honnêtement défini
```

---

*Document généré pour le projet NotMiddlingMe — ENI Fianarantsoa*  
*Système Mobile de Détection de MITM sur le Réseau Local*
