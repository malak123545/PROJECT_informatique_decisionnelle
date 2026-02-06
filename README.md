# Projet d'Extraction de Données Yelp

Ce projet permet d'extraire les données de votre base de données PostgreSQL Yelp et de les transformer en objets Java.

## Structure du Projet

```
PROJECT_informatique_decisionnelle/
├── src/
│   ├── connexion.java           # Classe de connexion à la base de données
│   ├── Main.java                # Programme principal de test
│   ├── model/                   # Classes modèles (POJO)
│   │   ├── User.java
│   │   ├── Review.java
│   │   ├── Elite.java
│   │   └── Friend.java
│   └── dao/                     # Classes DAO (Data Access Object)
│       ├── UserDAO.java
│       ├── ReviewDAO.java
│       ├── EliteDAO.java
│       └── FriendDAO.java
├── bin/                         # Fichiers compilés (.class)
├── postgresql-42.7.1.jar        # Driver JDBC PostgreSQL
├── compile.sh                   # Script de compilation
└── run.sh                       # Script d'exécution
```

## Classes Modèles

### User.java
Représente un utilisateur Yelp avec tous ses attributs (nom, étoiles moyennes, nombre de reviews, etc.)

### Review.java
Représente une review avec le texte, les étoiles, la date, etc.

### Elite.java
Représente le statut Elite d'un utilisateur pour une année donnée.

### Friend.java
Représente une relation d'amitié entre deux utilisateurs.

## Classes DAO

Chaque DAO fournit des méthodes pour extraire les données de la base :

- `getAllXXX()` : Récupère tous les enregistrements
- `getXXXById()` : Récupère un enregistrement par son ID
- `getXXXLimit(int limit)` : Récupère les N premiers enregistrements
- Et d'autres méthodes spécifiques...

## Compilation

Pour compiler le projet :

```bash
./compile.sh
```

## Exécution

Pour exécuter le programme de test :

```bash
./run.sh
```

Ou manuellement :

```bash
cd bin
java -cp ".:../../postgresql-42.7.1.jar" Main
```

## Utilisation dans votre Code

### Exemple 1 : Récupérer tous les utilisateurs

```java
import dao.UserDAO;
import model.User;
import java.sql.Connection;
import java.util.List;

Connection conn = connexion.getConnection();
List<User> users = UserDAO.getAllUsers(conn);

for (User user : users) {
    System.out.println(user.getName() + " - " + user.getAverageStars() + " étoiles");
}

connexion.closeConnection();
```

### Exemple 2 : Récupérer les reviews d'un utilisateur

```java
import dao.*;
import model.*;
import java.sql.Connection;
import java.util.List;

Connection conn = connexion.getConnection();

// Récupérer un utilisateur par son ID
User user = UserDAO.getUserById(conn, "ntlvfPzc8eglqvk92iDIAw");

// Récupérer ses reviews
List<Review> reviews = ReviewDAO.getReviewsByUserId(conn, user.getUserId());

System.out.println(user.getName() + " a écrit " + reviews.size() + " reviews");

connexion.closeConnection();
```

### Exemple 3 : Récupérer les amis d'un utilisateur

```java
import dao.*;
import model.*;
import java.sql.Connection;
import java.util.List;

Connection conn = connexion.getConnection();

// Récupérer les amis d'un utilisateur
List<Friend> friends = FriendDAO.getFriendsByUserId(conn, "ntlvfPzc8eglqvk92iDIAw");

System.out.println("Nombre d'amis: " + friends.size());

for (Friend friend : friends) {
    User friendUser = UserDAO.getUserById(conn, friend.getFriendId());
    if (friendUser != null) {
        System.out.println("  - " + friendUser.getName());
    }
}

connexion.closeConnection();
```

## Configuration

La connexion à la base de données est configurée dans `connexion.java`:

- **Host:** stendhal.iem:5432
- **Database:** tpid2020
- **User:** tpid
- **Password:** tpid

## Prérequis

- Java 17.0.17 (installé via SDKMAN)
- Driver PostgreSQL JDBC (postgresql-42.7.1.jar)
- Accès à la base de données PostgreSQL

## Tables Disponibles

Le projet supporte actuellement les tables suivantes du schéma `yelp`:

1. **user** - Utilisateurs Yelp
2. **review** - Reviews des utilisateurs
3. **elite** - Statut Elite des utilisateurs
4. **friend** - Relations d'amitié

## Notes

- Les tables du schéma `madm` nécessitent des permissions supplémentaires
- Assurez-vous que le serveur `stendhal.iem` est accessible depuis votre machine
