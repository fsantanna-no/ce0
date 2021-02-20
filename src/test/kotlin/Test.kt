import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(Alphanumeric::class)
class Tests {

    @Test
    fun a00_hello () {
        println("Hello World!")
    }
}