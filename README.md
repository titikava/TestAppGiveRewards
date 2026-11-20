# Rewards API

Учебный REST-сервис на Spring Boot и PostgreSQL. В каталоге хранится тип награды, например WELCOME. При выдаче появляется отдельная запись с владельцем, временем окончания и статусом. Идентификатор этой записи нужен для использования награды.

## Первый запуск на macOS

1. Установи Docker Desktop для своего процессора (Apple Silicon или Intel), открой его из Applications и дождись запуска движка. Docker Compose входит в комплект. [Инструкция Docker для macOS](https://docs.docker.com/desktop/setup/install/mac-install/)
2. Открой проект в IntelliJ IDEA и выбери **View → Tool Windows → Terminal**. Подойдёт и обычный Terminal, открытый в каталоге проекта рядом с `compose.yaml`.
3. Выполни команды ниже. При первом запуске Docker скачает образы, Gradle соберёт приложение, а Flyway создаст таблицы. Флаг `--wait` дождётся готовности базы и API. Для этого запуска достаточно Docker Desktop.

```sh
docker compose version
docker compose up --build --wait
docker compose ps
curl -fsS http://localhost:8080/actuator/health
```

Ожидай `"status":"UP"`. Базовый адрес API — `http://localhost:8080/api/v1`. Спецификация доступна без авторизации по `http://localhost:8080/openapi.yaml`. Если порт занят, запусти `API_PORT=8081 docker compose up --build --wait` и используй 8081 в запросах и настройках клиента.

### Управление через Docker Desktop

Открой **Containers → rewards-lab**. Внутри будут `api` и `db`. Нажми на `api` и открой **Logs**, чтобы увидеть запуск Spring. Для остановки или повторного запуска используй **Stop** и **Start** в строке всего проекта. После изменения исходников снова выполни `docker compose up --build --wait`. [Управление контейнерами в Docker Desktop](https://docs.docker.com/desktop/use-desktop/container/)

| Задача | Команда |
|---|---|
| Смотреть логи приложения | `docker compose logs -f api` (выход через Ctrl+C) |
| Остановить стенд и Kibana с сохранением данных | `./stop.sh` |
| Снова поднять сервис и дождаться готовности | `docker compose up --wait` |
| Удалить контейнеры с сохранением данных | `docker compose down` |
| Начать с пустой базы | `docker compose down -v`, затем `docker compose up --wait` |

**Сброс с `-v` удаляет всех пользователей, награды, выдачи и ключи повторных запросов этого стенда.** Удаление томов профиля `logs` также стирает историю логов. При обычном перезапуске данные сохраняются.

## Поиск логов в Kibana

В каталоге проекта выполни:

```sh
docker compose --profile logs up --build --wait
```

Docker Desktop скачает Elasticsearch, Kibana и Filebeat при первом запуске. После перезагрузки компьютера открой Docker Desktop и выполни `docker compose --profile logs up --wait`. Для работы только с API и PostgreSQL после остановки стенда достаточно `docker compose up --wait`.

1. Открой [Kibana Discover](http://localhost:5601/app/discover). На локальном стенде вход без пароля, порт Kibana доступен только с этого компьютера. Elasticsearch работает внутри сети Docker.
2. Выбери представление данных **Rewards API**. Оно создаётся автоматически для индексов `rewards-api-*`. Для примеров ниже используй строку поиска **KQL** в классическом режиме Discover.
3. Отправь запрос к `/api/v1/…` из Postman. В **ответе → Headers** скопируй значение `X-Request-Id`. Каждая попытка получает новый UUID, в том числе при повторе с тем же `Idempotency-Key`.
4. В Discover введи `requestId: "скопированный-UUID"` и нажми Enter. Доставка обычно занимает несколько секунд. Если записи пока нет, нажми **Refresh** и проверь диапазон времени справа вверху. Разверни найденную строку, чтобы увидеть `action`, `httpStatus`, `durationMs`, а при ошибке — `errorCode` и `reason`.

| Что искать | Пример KQL |
|---|---|
| Действия с конкретной наградой или пользователем | `rewardId: 4` или `userId: 100001` |
| Попытки выдать награду конкретному пользователю | `action: issueReward AND rewardId: 4 AND userId: 100001` |
| Ошибки разбора и валидации при выдаче | `action: issueReward AND httpStatus: 400` |
| Повторы, вернувшие исходную квитанцию | `outcome: replayed` |
| Отказ при использовании просроченной награды | `errorCode: GRANT_EXPIRED` |

Если разбор JSON или проверка заголовков завершилась до чтения идентификаторов, используй `requestId`. Для успешной выдачи доступны `grantId` и `expiresAt`. В момент истечения секунды отдельная запись не создаётся: статус вычисляется при обращении. При попытке использовать просроченную награду появится событие с причиной отказа.

API пишет JSON в консоль и общий том. Filebeat читает его и отправляет записи в Elasticsearch. Политика хранения удаляет суточные индексы после семи дней. В томе приложения файлы ротируются, объём архивов ограничен 100 МБ. Сборщик запоминает позицию чтения в собственном томе. Записи о запросах содержат идентификаторы и диагностические поля; пароли, заголовок Authorization и полные тела запросов в них не попадают.

| Задача | Команда |
|---|---|
| Смотреть события API в терминале | `docker compose logs --tail=50 -f api` |
| Проверить состояние компонентов | `docker compose --profile logs ps -a` (для одноразового `logs-setup` нормален статус `Exited (0)`) |
| Диагностировать доставку логов | `docker compose --profile logs logs --tail=50 filebeat logs-setup` |
| Остановить поиск логов, оставив API и базу запущенными | `docker compose --profile logs stop filebeat kibana elasticsearch` |
| Остановить весь стенд | `./stop.sh` |

## Коллекция Postman

В рабочем пространстве Postman нажми **Import** и выбери [Rewards-API.postman_collection.json](postman/Rewards-API.postman_collection.json). После запуска стенда открой **Rewards API** и выбери **Run collection** или отправляй запросы сверху вниз. Коллекция создаст нового пользователя и награду, сохранит ID и подставит нужные учётные данные для выдачи и использования. В последнем запросе можно увидеть, какую квитанцию возвращает повтор с тем же ключом.

Оставь **No environment**. Открой **Collections → Rewards API → Variables**: там заданы `baseUrl`, `adminLogin`, `adminPassword`, `userPassword` и `validitySeconds`. Если запускал Compose с другим портом или паролем, измени значения в коллекции.

Запросы создания сохраняют `userId`, `userEmail` и `rewardId` из ответов через `pm.collectionVariables.set(...)` во вкладке **Scripts → Post-response**. При успешном создании пользователя или награды скрипт генерирует новый `idempotencyKey` через встроенную переменную Postman `{{$guid}}`. После выдачи таким же способом из ответа сохраняется `grantId`. Эти значения подставляются в последующие запросы через `{{имяПеременной}}`.

## Формат идентификаторов

`userId` — целое число от 100000 до 999999999999. PostgreSQL начинает нумерацию пользователей с 100000. Успешно созданные награды получают `rewardId` по порядку: 1, 2, 3. В JSON передавай оба поля числами, например `{"userId":100000,"rewardId":1}`. ID отдельной выдачи (`grantId`) и заголовок `Idempotency-Key` имеют формат UUID.

При обновлении стенда командой `docker compose up --build --wait` Flyway перенесёт существующие связи и историю. Награды из старой базы получат номера по коду, поскольку в прежней схеме время создания награды не записывалось. В DBeaver прежний UUID можно сопоставить с новым `id` по колонке `legacy_id` таблиц `app_user` и `reward`. В Postman повторно импортируй коллекцию и запусти её сверху вниз, чтобы заполнить переменные числовыми ID.

## Учётные записи и операции

Используется HTTP Basic. Локальный администратор по умолчанию — `admin`, пароль — `local-admin-pass`. Его можно задать через `ADMIN_PASSWORD` при старте Compose. Администратор создаёт пользователей с email и паролем. Пользователь входит с этими данными. Email при сохранении приводится к нижнему регистру.

Все пути в таблице начинаются с `/api/v1`.

| Метод и путь | Доступ | Результат |
|---|---|---|
| `POST /users` | ADMIN | Новый профиль, `201` |
| `GET /users/{userId}` | ADMIN или владелец | Профиль |
| `POST /rewards` | ADMIN | Новая награда в каталоге, `201` |
| `PUT /rewards/{rewardId}` | ADMIN | Замена названия, описания, активности, срока и лимита |
| `GET /rewards/{rewardId}` | ADMIN и USER | Карточка награды |
| `GET /rewards?active=true&page=0&size=20` | ADMIN и USER | Каталог по коду в алфавитном порядке |
| `POST /grants` | ADMIN | Выдача по `userId` и `rewardId`, `201`. Обязателен заголовок `Idempotency-Key` с UUID |
| `GET /users/{userId}/grants` | ADMIN или владелец | История пользователя с текущими статусами |
| `GET /rewards/{rewardId}/grants` | ADMIN | Выдачи конкретной награды с ID получателей |
| `POST /users/{userId}/grants/{grantId}/use` | ADMIN или владелец | Использованная выдача, `200` |

У обоих списков выдач есть фильтр `status` и пагинация `page`/`size`. Страница начинается с 0, размер по умолчанию 20, допустимый диапазон 1–100. В ответе есть `items` и `hasNext`. Выдачи упорядочены от новых к старым, при совпадении времени — по ID.

## От создания пользователя до использования награды

Блок можно целиком выполнить в одном терминале macOS после запуска. Для извлечения ID используется `python3`. Каждый запуск создаёт новую пару пользователя и награды. В конце в истории появится выдача со статусом `USED`.

```sh
api="http://localhost:${API_PORT:-8080}/api/v1"
admin="admin:${ADMIN_PASSWORD:-local-admin-pass}"
suffix=$(uuidgen | tr -d '-')
email="qa-$suffix@example.test"
key=$(uuidgen)
json_id() { python3 -c 'import json, sys; print(json.load(sys.stdin)["id"])'; }

user_id=$(curl -fsS -u "$admin" -H 'Content-Type: application/json' \
  -d "{\"name\":\"QA\",\"email\":\"$email\",\"password\":\"practice-pass\"}" \
  "$api/users" | json_id)

reward_id=$(curl -fsS -u "$admin" -H 'Content-Type: application/json' \
  -d "{\"code\":\"WELCOME_$suffix\",\"name\":\"Welcome bonus\",\"issuanceLimit\":3}" \
  "$api/rewards" | json_id)

grant_id=$(curl -fsS -u "$admin" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" -d "{\"userId\":$user_id,\"rewardId\":$reward_id}" \
  "$api/grants" | json_id)

curl -fsS -u "$email:practice-pass" "$api/users/$user_id/grants?status=ACTIVE"
curl -fsS -u "$email:practice-pass" -X POST "$api/users/$user_id/grants/$grant_id/use"
curl -fsS -u "$email:practice-pass" "$api/users/$user_id/grants?status=USED"
```

## Правила для автотестов

- **Срок жизни.** `validitySeconds` отсчитывается от момента выдачи, по умолчанию это 604800 секунд (7 суток). При `now < expiresAt` награда активна. Ровно в `expiresAt` неиспользованная награда становится `EXPIRED`. Использованная остаётся `USED`. Для быстрой проверки истечения задай срок 1–2 секунды и опрашивай историю до `EXPIRED`.
- **Изменение награды.** Код постоянен. При `PUT` передаются `name`, `active` и `validitySeconds`. Пропущенные `description` и `issuanceLimit` очищаются. Уже выданные экземпляры сохраняют исходные название и срок. Деактивация закрывает новые выдачи, ранее выданную активную награду всё ещё можно использовать.
- **Лимит и дубликаты.** Один пользователь может получить конкретную награду один раз за всё время. Использованные и истёкшие выдачи учитываются в лимите. Если два разных пользователя одновременно претендуют на последнее место, один запрос вернёт `201`, второй — `409 LIMIT_REACHED`.
- **Повтор запроса.** После успешной выдачи тот же ключ и те же ID вернут исходную квитанцию с `201`, первоначальным `ACTIVE` и тем же ID выдачи. Другие ID с занятым ключом дают `409 IDEMPOTENCY_KEY_REUSED`. Новый ключ для уже выданной пары даёт `409 ALREADY_GRANTED`. Неуспешная операция оставляет ключ свободным для повтора.
- **Ошибки.** Тело содержит `code` и `message`, например `{"code":"GRANT_USED","message":"Grant is USED."}`. В проверках удобно опираться на HTTP-статус и стабильный `code`. Ошибка входа даёт `401`, обращение пользователя к чужой истории — `403`, запрос администратора к отсутствующему объекту — `404`.

## Java-клиент и собственные тесты

Контракт хранится в [openapi.yaml](src/main/resources/static/openapi.yaml). У модуля `api-tests` есть отдельные зависимости JUnit Jupiter 5, AssertJ, REST Assured и Jackson. Для локальной работы с Gradle используй JDK 21 или совместимый более новый JDK. Открой корневой проект в IDEA как Gradle-проект и дождись синхронизации.

```sh
./gradlew :api-tests:generateClient
./gradlew :api-tests:classes
```

OpenAPI Generator создаст Java-модели и классы `UsersApi`, `RewardsApi`, `GrantsApi` в `api-tests/build/generated/rewards-client/src/main/java`. Каталог уже включён в сборку. Свои тестовые классы размещай в `api-tests/src/test/java`. При изменении контракта Gradle обновит клиент перед компиляцией. [Настройки Java-генератора](https://openapi-generator.tech/docs/generators/java/)

Пример вызова из метода теста возвращает типизированный `RewardPage` даже при пустом каталоге.

```java
import io.restassured.RestAssured;
import org.example.rewards.client.ApiClient;
import org.example.rewards.client.model.RewardPage;

// Внутри метода
var client = ApiClient.api(ApiClient.Config.apiConfig());
var auth = RestAssured.preemptive().basic("admin", "local-admin-pass");
RewardPage page = client.rewards().listRewards()
        .reqSpec(spec -> spec.setAuth(auth))
        .sizeQuery(10)
        .executeAs(response -> response.then().statusCode(200).extract().response());
```

Если изменил порт, добавь `.setBaseUri("http://localhost:8081/api/v1")` к `spec` рядом с `.setAuth(auth)`. Для проверки ошибок получай сырой ответ через `.execute(response -> response)`. Запускай свои тесты при работающем API из IDEA кнопкой рядом с классом или командой `./gradlew :api-tests:test`.
