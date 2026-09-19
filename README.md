# Trigger Demo

Учебный проект для демонстрации:
- аутентификации;
- авторизации по ролям ADMIN/MANAGER;
- ограничений БД CHECK / NOT NULL;
- CRUD товаров;
- PostgreSQL trigger для аудита изменения цены.

## База данных

Создайте БД:

```sql
CREATE DATABASE trigger_demo;
```

В `src/main/resources/application.properties` при необходимости измените логин/пароль PostgreSQL.

## Запуск

```bash
mvn spring-boot:run
```

Откройте http://localhost:8080

## Демонстрация триггера

1. Войдите как manager.
2. Откройте товар и измените цену.
3. Сохраните.
4. Откройте `/audit` — менеджер получит отказ, потому что журнал доступен только ADMIN.
5. Выйдите и войдите как admin.
6. Откройте «Журнал изменений».
7. Покажите появившуюся запись.

При изменении цены Java выполняет обычный UPDATE через JPA/Hibernate. INSERT в `audit_log` приложение не выполняет: его автоматически выполняет PostgreSQL trigger.
