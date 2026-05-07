# ICT304 — API REST de Paiement

> Projet de démonstration des techniques de test logiciel (ICT 304)  
> Stack : Spring Boot 3 · JPA/H2 · JUnit 5 · Mockito · PIT Mutation · JMeter

---

## Démarrage rapide (local)

```bash
# 1. Cloner / ouvrir dans IntelliJ IDEA ou VSCode
# 2. Compiler et lancer
mvn spring-boot:run

# L'API est disponible sur :
# http://localhost:8080/swagger-ui.html  ← interface graphique
# http://localhost:8080/h2-console       ← base de données H2
# http://localhost:8080/actuator/health  ← health check
```

---

## Endpoints de l'API

| Méthode | URL                              | Description              |
|---------|----------------------------------|--------------------------|
| POST    | /api/v1/accounts                 | Créer un compte          |
| GET     | /api/v1/accounts                 | Lister les comptes       |
| GET     | /api/v1/accounts/{num}           | Voir un compte           |
| GET     | /api/v1/accounts/{num}/balance   | Consulter le solde       |
| POST    | /api/v1/accounts/deposit         | Déposer de l'argent      |
| POST    | /api/v1/accounts/withdraw        | Retirer de l'argent      |
| POST    | /api/v1/accounts/transfer        | Transférer entre comptes |
| GET     | /api/v1/accounts/{num}/history   | Historique               |

---

## Exemples cURL

```bash
# Créer un compte
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "Alice Dupont", "initialBalance": 1000}'

# Déposer
curl -X POST http://localhost:8080/api/v1/accounts/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "PAY-XXXXXXXX", "amount": 500, "description": "Salaire"}'

# Transférer
curl -X POST http://localhost:8080/api/v1/accounts/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber": "PAY-AAA", "toAccountNumber": "PAY-BBB", "amount": 200}'
```

---

## Exécuter les tests

```bash
# Tests unitaires + intégration
mvn test

# Tests de mutation avec PIT (rapport HTML dans target/pit-reports/)
mvn test-compile org.pitest:pitest-maven:mutationCoverage

# Voir le rapport de mutation
open target/pit-reports/index.html   # macOS
xdg-open target/pit-reports/index.html  # Linux

# Tests de performance JMeter (après avoir lancé l'API)
# 1. Créer d'abord un compte de test avec le numéro PAY-TEST001
# 2. Lancer JMeter
jmeter -n -t payment-api-test.jmx -l results.jtl -e -o rapport-perf/
open rapport-perf/index.html
```

---

## Déploiement sur Railway

```bash
# Méthode 1 : Via GitHub (recommandée)
# 1. Pousser ce repo sur GitHub
# 2. Aller sur railway.app → New Project → Deploy from GitHub
# 3. Railway détecte automatiquement le Dockerfile
# 4. Ajouter les variables d'environnement :
#    DATABASE_URL = postgresql://...  (fourni par Railway PostgreSQL)
#    DB_USER      = postgres
#    DB_PASSWORD  = (fourni par Railway)
#    DB_DRIVER    = org.postgresql.Driver
#    JPA_DIALECT  = org.hibernate.dialect.PostgreSQLDialect

# Méthode 2 : CLI Railway
npm install -g @railway/cli
railway login
railway init
railway up
```

### Variables d'environnement Railway

| Variable       | Valeur                                    |
|----------------|-------------------------------------------|
| DATABASE_URL   | `postgresql://user:pass@host:port/dbname` |
| DB_USER        | fourni par Railway PostgreSQL             |
| DB_PASSWORD    | fourni par Railway PostgreSQL             |
| DB_DRIVER      | `org.postgresql.Driver`                   |
| JPA_DIALECT    | `org.hibernate.dialect.PostgreSQLDialect` |
| PORT           | `8080` (Railway l'injecte automatiquement)|

---

## Déploiement sur Render

```bash
# 1. Créer un nouveau Web Service sur render.com
# 2. Connecter votre repo GitHub
# 3. Render détecte le Dockerfile automatiquement
# 4. Ajouter un PostgreSQL database (Free plan disponible)
# 5. Copier DATABASE_URL depuis les paramètres de la base
# 6. Ajouter les variables dans "Environment"
```

---

## Structure du projet ICT304

```
payment-api/
├── src/main/java/cm/ict304/payment/
│   ├── model/          # Entités JPA (Account, Transaction)
│   ├── repository/     # Interfaces Spring Data JPA
│   ├── service/        # ← CŒUR : logique métier testable
│   ├── controller/     # Endpoints REST
│   ├── dto/            # Objets de transfert de données
│   └── exception/      # Exceptions métier + handler global
│
├── src/test/java/cm/ict304/payment/
│   ├── service/        # Tests unitaires (boîte blanche, DFT, mutation)
│   └── controller/     # Tests d'intégration (Spring MockMvc)
│
├── payment-api-test.jmx  # Plan de test JMeter (performance)
└── Dockerfile            # Build multi-stage pour Railway/Render
```

---

## Techniques ICT304 appliquées

| Technique | Où | Outils |
|-----------|-----|--------|
| Tests boîte blanche (C1/C2) | AccountServiceTest | JUnit 5 |
| Tests boîte noire (valeurs limites) | AccountServiceTest | @ParameterizedTest |
| Tests de flux de données (DFT) | Commentaires CFG dans service | JUnit 5 |
| Tests de mutation | AccountServiceTest | PIT (pitest-maven) |
| Tests d'intégration | AccountControllerIntegrationTest | MockMvc + H2 |
| Tests de performance | payment-api-test.jmx | Apache JMeter |

---

## Interprétation du score de mutation PIT

| Score | Interprétation |
|-------|----------------|
| < 60% | Suite de tests insuffisante |
| 60–80% | Acceptable pour un projet académique |
| 80–90% | Bonne couverture — standard professionnel |
| > 90%  | Excellente couverture |
# system-pay-API
