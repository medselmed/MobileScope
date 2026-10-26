# MobileScope

Application Android d'analyse du mobile (Kotlin, Jetpack Compose) : connexion Google, infos appareil, cartes détaillées
(RAM, batterie, réseau, stockage, contacts, appels, SMS, applications) et mise à jour intégrée.

## Obtenir l'APK (sans installer Android Studio)
1. Créez un dépôt GitHub **public** et envoyez-y tout ce dossier (branche `main`).
2. Onglet **Actions** : le workflow « Build & publier l'APK » compile et publie l'APK dans **Releases** (≈ 5 à 8 min).
3. Téléchargez `MobileScope-1.0.N.apk` depuis la release et installez-le (autoriser « sources inconnues »).

## Connexion Google (une seule fois)
1. Google Cloud Console > nouveau projet > Écran de consentement OAuth (type Externe, ajoutez votre compte en testeur).
2. Identifiants > Créer un ID client OAuth :
   - type **Android** : package `com.medsel.mobilescope`, empreinte SHA-1 `38:52:51:89:D0:8C:A5:C5:11:2D:99:46:52:6B:68:EE:EC:18:53:8E`
   - type **Application Web** : copiez son ID client.
3. GitHub > Settings > Secrets and variables > Actions > nouveau secret `WEB_CLIENT_ID` = l'ID client **Web**.
4. Relancez le workflow. Sans ce secret, le bouton « Continuer sans compte » reste disponible.

## Mises à jour
Modifiez le code, faites un `git push` : une nouvelle release `v1.0.N+1` est publiée. Au lancement (ou via « Vérifier »),
l'app détecte la version, télécharge l'APK et ouvre l'installateur Android. Les données sont conservées.

## À savoir
- `app/release.jks` (mot de passe `mobilescope`) signe toutes les versions : ne le changez jamais, sinon Android refusera la mise à jour.
  Pour une diffusion publique, déplacez ce keystore dans des secrets GitHub.
- Permissions spéciales : « Accès aux données d'utilisation » (réglage manuel) pour le trafic par app, le temps d'usage et le poids des apps.
- Journal d'appels et SMS : usage personnel/sideload uniquement ; le Play Store les refuse sauf cas particuliers.
