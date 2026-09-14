# [Enfants des copains]

Une application Android simple, légère et respectueuse de la vie privée pour gérer les anniversaires des enfants de vos amis. Développée dans une démarche de logiciel libre.

[![License: GPL-v3](https://img.shields.io/badge/gpl-v3)](https://opensource.org/license/gpl-3.0)
[![Available on F-Droid](https://img.shields.io/f-droid/v/ch.ecoandco.enfantsDesCopains?label=F-Droid)](https://f-droid.org/fr/packages//)
*(Le badge F-Droid apparaîtra automatiquement une fois l'app publiée)*

## 🛡️ Philosophie & Vie Privée

Cette application est conçue selon les principes de **Keep Android Open** et de la vie privée par défaut :

*   **100% Hors Ligne :** Aucune connexion Internet n'est requise. Toutes les données sont stockées localement sur votre appareil.
*   **Zéro Collecte de Données :** Pas de traceurs, pas de publicités, pas de Google Analytics, pas de Firebase.
*   **Code Transparent :** Le code source est entièrement auditable. Vous savez exactement ce que fait l'application.
*   **Léger :** Pas de dépendances lourdes inutiles.

## ✨ Fonctionnalités

*   Ajout et gestion des prénoms et dates de naissance.
*   Calcul automatique de l'âge et du prochain anniversaire.
*   Interface simple et intuitive, sans distractions.
*   Export/Import des données au format JSON.
*   Thème sombre/clair.

## 📥 Installation

### Via F-Droid (Recommandé)
L'application est bientôt disponible sur le magasin d'applications libres [F-Droid](https://f-droid.org/).
C'est la méthode la plus sûre pour recevoir les mises à jour automatiques et vérifier la signature du code.

*"Bientôt disponible sur F-Droid"*

### Installation manuelle (APK)
Vous pouvez télécharger la dernière version signée directement depuis la section [Releases](https://github.com/Genentes/Genentes-GtB/releases) de ce dépôt.
1. Téléchargez le fichier `.apk` le plus récent.
2. Autorisez l'installation de sources inconnues sur votre appareil Android.
3. Installez le fichier.

## 🛠️ Construction (Pour les développeurs)

Ce projet est construit avec Android Studio. Vous pouvez compiler l'application vous-même pour vérifier la reproductibilité du binaire.

**Prérequis :**
*   Android Studio (ou IntelliJ IDEA avec plugin Android)
*   JDK 21

**Étapes :**
1. Clonez le dépôt :
   ```bash
   git clone https://github.com/Genentes/Genentes-GtB.git
   cd Genentes-GtB
Ouvrez le projet dans Android Studio.
Lancez la build (Build > Build Bundle(s) / APK(s) > Build APK(s)).
Ou en ligne de commande :

bash
./gradlew assembleDebug

🤝 Contribuer
Les contributions sont les bienvenues ! Que ce soit pour corriger des bugs, améliorer l'interface ou traduire l'application.

Ouvrez une Issue pour signaler un bug ou proposer une idée.
Soumettez une Pull Request pour proposer des modifications de code.

📄 Licence
Ce projet est distribué sous la licence GNU General Public License v3 (GPL-3.0). Vous êtes libre d'utiliser, modifier et distribuer ce logiciel, à condition que toute œuvre dérivée soit également publiée sous cette même licence et que le code source reste accessible. Voir le fichier LICENSE pour le texte complet de la licence.

📞 Contact
Dépôt GitHub : https://github.com/Genentes/Genentes-GtB
Issues : https://github.com/Genentes/Genentes-GtB/issues
