# Spring Integration

Octavius provides seamless integration with the Spring Framework and Spring Boot via the `driver-spring-integration` module.

## Setup

Add the integration module to your project:

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("io.github.octavius-framework:driver-spring-integration:<version>")
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

## Transaction Management

The auto-configured `PlatformTransactionManager` supports Spring's declarative transaction management (`@Transactional`). Connections are properly synchronized, meaning `template.execute` will reuse the active transaction's session.

By default, **nested transactions are allowed**. This means you can use `Propagation.NESTED` safely with the Octavius driver.

```kotlin
@Transactional(propagation = Propagation.NESTED)
fun updateUserDetails() {
    // Executes inside a savepoint if an active transaction already exists
}
```
