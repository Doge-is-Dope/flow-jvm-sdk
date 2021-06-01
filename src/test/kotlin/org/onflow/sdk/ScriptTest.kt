package org.onflow.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.onflow.sdk.cadence.CDIFConverter
import org.onflow.sdk.cadence.CadenceDataInterchangeFormatConverter
import org.onflow.sdk.cadence.Field
import org.onflow.sdk.cadence.StringField
import org.onflow.sdk.cadence.StructField
import org.onflow.sdk.cadence.cdif
import org.onflow.sdk.cadence.parseCdif
import org.onflow.sdk.test.FlowEmulatorTest
import java.math.BigDecimal

@CDIFConverter(TestClassConverter::class)
open class TestClass(
    val address: FlowAddress,
    val balance: BigDecimal,
    val hashAlgorithm: HashAlgorithm,
    val isValid: Boolean
)

class TestClassConverter : CadenceDataInterchangeFormatConverter<TestClass> {
    override fun unmarshall(value: Field<*>): TestClass = parseCdif(value) {
        TestClass(
            address = FlowAddress(address("address")),
            balance = bigDecimal("balance"),
            hashAlgorithm = enum("hashAlgorithm"),
            isValid = boolean("isValid")
        )
    }

    override fun marshall(value: TestClass): Field<*> {
        return cdif {
            struct {
                compositeOfPairs("TestClass") {
                    listOf(
                        "address" to address(value.address.base16Value),
                        "balance" to ufix64(value.balance),
                        "signatureAlgorithm" to enum(value.hashAlgorithm),
                        "isValid" to boolean(value.isValid)
                    )
                }
            }
        }
    }
}

@FlowEmulatorTest
class ScriptTest {

    @Test
    fun `Can execute a script`() {
        val accessAPI = Flow.newAccessApi("localhost", 3570)

        val result = accessAPI.simpleFlowScript {
            script {
                """
                    pub fun main(): String {
                        return "Hello World"
                    }
                """
            }
        }

        assertTrue(result.cdif is StringField)
        assertEquals("Hello World", result.cdif.value)
    }

    @Test
    fun `Can input and export arguments`() {
        val accessAPI = Flow.newAccessApi("localhost", 3570)
        val address = "e467b9dd11fa00df"

        val result = accessAPI.simpleFlowScript {
            script {
                """
                    pub struct TestClass {
                        pub let address: Address
                        pub let balance: UFix64
                        pub let hashAlgorithm: HashAlgorithm
                        pub let isValid: Bool
                        
                        init(address: Address, balance: UFix64, hashAlgorithm: HashAlgorithm, isValid: Bool) {
                            self.address = address
                            self.balance = balance
                            self.hashAlgorithm = hashAlgorithm
                            self.isValid = isValid
                        }
                    }
                    
                    pub fun main(address: Address): TestClass {
                        return TestClass(
                            address: address,
                            balance: UFix64(1234),
                            hashAlgorithm: HashAlgorithm.SHA3_256,
                            isValid: true
                        )
                    }
                """
            }
            arg { address(address) }
        }

        assertTrue(result.cdif is StructField)
        val struct = Flow.unmarshall(TestClass::class, result.cdif)
        assertEquals(address, struct.address.base16Value)
        assertEquals(BigDecimal("1234"), struct.balance.stripTrailingZeros())
        assertEquals(HashAlgorithm.SHA3_256, struct.hashAlgorithm)
        assertTrue(struct.isValid)
    }
}
