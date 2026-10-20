package app.astra.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreSuiteTest {
    @Test fun goldenFixturesAndRules() {
        assertTrue("Core suite failed — see output above", CoreSuite(File("src/test/resources/fixtures")).run())
    }
}
