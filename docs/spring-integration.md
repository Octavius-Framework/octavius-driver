# Spring Integration

Octavius provides seamless integration with the Spring Framework and Spring Boot via the `driver-spring-integration` module.

## Setup

Add the integration module to your project:

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    // Brings the driver in transitively - no separate dependency needed
    implementation("io.github.octavius-framework:driver-spring-integration:0.9.4")
}
```

## Spring Boot Auto-Configuration

If you are using Spring Boot, the module will automatically configure everything you need:

1. **`OctaviusTemplate`**: A specialized template bean that handles executing operations within an `OctaviusSession`.
2. **`PlatformTransactionManager`**: A customized `JdbcTransactionManager` that enables nested transactions and uses `OctaviusExceptionTranslator`.
3. **Exception Translation**: Any `OctaviusException` and exceptions from `SQLExceptionWrapper` are wrapped in `OctaviusDataAccessException`. Other `SQLException`s (e.g., from Hikari) are translated into Spring's standard `DataAccessException` hierarchy.

You only need to configure the data source in your `application.yml` or `application.properties`:

```yaml
spring:
  datasource:
    url: jdbc:octavius://localhost:5432/my_database
    username: my_user
    password: my_password
    driver-class-name: io.github.octaviusframework.driver.jdbc.OctaviusDriver
```

## Using `OctaviusTemplate`

Once auto-configured, you can inject `OctaviusTemplate` into your services. It manages getting the connection, extracting the `OctaviusSession`, and translating exceptions. The session is the receiver of the `execute` block, so its operations are called directly on `this`.

```kotlin
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.spring.OctaviusTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val template: OctaviusTemplate) {

    @Transactional
    fun createUser(id: Int, name: String) {
        template.execute {
            createNamedQuery("INSERT INTO users (id, name) VALUES (@id, @name)")
                .update("id" to id, "name" to name)
        }
    }
    
    @Transactional(readOnly = true)
    fun getUser(id: Int): String {
        return template.execute {
            val row = createNamedQuery("SELECT name FROM users WHERE id = @id")
                .fetchRow("id" to id)
                
            row?.get<String>("name") ?: throw RuntimeException("User not found")
        }
    }
}
```

### Connection lifetime

`execute` takes its connection through `DataSourceUtils`, so it joins an active `@Transactional` transaction rather than opening its own, and hands the connection back at the end. The session it wraps around that connection is never closed — the connection's lifetime belongs to Spring, not to the session.

One consequence follows from that: **the tidying a session does when you close it yourself does not happen here.** A `LISTEN` registered inside an `execute` block stays on the connection and rides back into the pool with it; so does a `COPY` you started and never finished. Measured on a pool of one, a channel registered inside a block was still registered afterwards.

Nothing exotic belongs in a template block, then. Keep it to queries, and give anything that outlives a single statement — a listener, a bulk transfer — a session of its own through [`getOctaviusSession`](initialization.md#getting-a-session), closed by you. A `COPY` you do finish inside the block is fine; only an abandoned one is a problem. See [What survives a return to the pool](initialization.md#what-survives-a-return-to-the-pool) for the wider rule.

## Transaction Management

The auto-configured `PlatformTransactionManager` supports Spring's declarative transaction management (`@Transactional`). Connections are properly synchronized, meaning `template.execute` will reuse the active transaction's session.

By default, **nested transactions are allowed**. This means you can use `Propagation.NESTED` safely with the Octavius driver.

```kotlin
@Transactional(propagation = Propagation.NESTED)
fun updateUserDetails() {
    // Executes inside a savepoint if an active transaction already exists
}
```
