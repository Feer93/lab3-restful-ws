package rest.addressbook

import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.http2.api.Session
import org.eclipse.jetty.http2.api.Stream
import org.eclipse.jetty.http2.api.server.ServerSessionListener
import org.eclipse.jetty.http2.client.HTTP2Client
import org.eclipse.jetty.http2.frames.DataFrame
import org.eclipse.jetty.http2.frames.HeadersFrame
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.FuturePromise
import org.eclipse.jetty.util.Jetty
import org.eclipse.jetty.util.Promise
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.*
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AddressBookServiceTest {

    @LocalServerPort
    var port = 8080

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun cleanRepository() {
        addressBook.clear()
    }

    @Test
    fun serviceIsAlive() {
        // Request the address book
        val response = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body?.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////

        val secondResponse = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        secondResponse.body?.let { assertEquals(0, it.size) }

    }

    @Test
    fun createUser() {

        // Prepare data
        val juan = Person(name = "Juan")
        val juanURI: URI = URI.create("http://localhost:$port/contacts/person/1")

        // Create a new user
        var response = restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)

        assertEquals(201, response.statusCode.value())
        assertEquals(juanURI, response.headers.location)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        var juanUpdated = response.body
        assertEquals(juan.name, juanUpdated?.name)
        assertEquals(1, juanUpdated?.id)
        assertEquals(juanURI, juanUpdated?.href)

        // Check that the new user exists
        response = restTemplate.getForEntity(juanURI, Person::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        juanUpdated = response.body
        assertEquals(juan.name, juanUpdated?.name)
        assertEquals(1, juanUpdated?.id)
        assertEquals(juanURI, juanUpdated?.href)

        //////////////////////////////////////////////////////////////////////
        // Verify that POST /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is not safe and not idempotent
        //////////////////////////////////////////////////////////////////////

        //This verifies that POST method is not safe as the previous state has changed,
        //we should now have that nextId value is 2 as it has incremented, and the size
        //of our personList should be 1 as our previous state was 0.
        assertEquals(2,addressBook.nextId)
        assertEquals(1,addressBook.personList.size)

        //To prove that POST method is not idempotent, we have to make a new POST request and
        //verify that the state of the server has changed  once again.
        val previousID = addressBook.nextId
        val previousSize = addressBook.personList.size
        //Creating a new User Juan
        restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)
        assertNotEquals(previousID, addressBook.nextId)
        assertNotEquals(previousSize, addressBook.personList.size)
    }

    @Test
    fun createUsers() {
        // Prepare server
        val salvador = Person(id = addressBook.nextId(), name = "Salvador")
        addressBook.personList.add(salvador)

        // Prepare data
        val juan = Person(name = "Juan")
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        val maria = Person(name = "Maria")
        val mariaURI = URI.create("http://localhost:$port/contacts/person/3")

        // Create a new user
        var response = restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)
        assertEquals(201, response.statusCode.value())
        assertEquals(juanURI, response.headers.location)

        // Create a second user
        response = restTemplate.postForEntity("http://localhost:$port/contacts", maria, Person::class.java)
        assertEquals(201, response.statusCode.value())
        assertEquals(mariaURI, response.headers.location)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)

        var mariaUpdated = response.body
        assertEquals(maria.name, mariaUpdated?.name)
        assertEquals(3, mariaUpdated?.id)
        assertEquals(mariaURI, mariaUpdated?.href)

        // Check that the new user exists
        response = restTemplate.getForEntity(mariaURI, Person::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        mariaUpdated = response.body
        assertEquals(maria.name, mariaUpdated?.name)
        assertEquals(3, mariaUpdated?.id)
        assertEquals(mariaURI, mariaUpdated?.href)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts/person/3 is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
        val response2 = restTemplate.getForEntity(mariaURI, Person::class.java)

        assertEquals(200, response2.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response2.headers.contentType)
        val newMariaUpdated = response2.body
        assertEquals(mariaUpdated?.name,newMariaUpdated?.name)
        assertEquals(3,newMariaUpdated?.id)
        assertEquals(mariaUpdated?.href,newMariaUpdated?.href)
    }

    @Test
    fun listUsers() {

        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Test list of contacts
        val response = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        assertEquals(2, response.body?.size)
        assertEquals(juan.name, response.body?.get(1)?.name)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
        val response2 = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response2.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response2.headers.contentType)
        assertEquals(response.body?.size, response2.body?.size)
        assertEquals(juan.name, response2.body?.get(1)?.name)
        assertEquals(salvador.name, response2.body?.get(0)?.name)


    }

    @Test
    fun updateUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Update Maria
        val maria = Person(name = "Maria")

        val PersonBeforePut = addressBook.personList.find { person -> person.name == juan.name }

        var response = restTemplate.exchange(juanURI, HttpMethod.PUT, HttpEntity(maria), Person::class.java)
        assertEquals(204, response.statusCode.value())

        val PersonAfterPut = addressBook.personList.find { person -> person.name == juan.name }

        // Verify that the update is real
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val updatedMaria = response.body
        assertEquals(maria.name, updatedMaria?.name)
        assertEquals(2, updatedMaria?.id)
        assertEquals(juanURI, updatedMaria?.href)

        // Verify that only existing values can be updated
        restTemplate.execute("http://localhost:$port/contacts/person/3", HttpMethod.PUT,
            {
                it.headers.contentType = MediaType.APPLICATION_JSON
                ObjectMapper().writeValue(it.body, maria)
            },
            { assertEquals(404, it.statusCode.value()) }
        )

        //////////////////////////////////////////////////////////////////////
        // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////

        /*Not safe -> put has changed the server state, so when we try to find our user "Juan"
          after the Put request, we shouldn't find him.
        * */
        assertNotNull(PersonBeforePut)
        assertNull(PersonAfterPut)

        /*Idempotent -> we always have the same replies from the server, if the put Request
        is the same it doesnt matter how many times we send the PUT request that we will always get the same reply
        */

        val newPutResponse = restTemplate.exchange(juanURI, HttpMethod.PUT, HttpEntity(maria), Person::class.java)
        assertEquals(204, newPutResponse.statusCode.value())

        val newresponse = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, newresponse.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, newresponse.headers.contentType)
        val newupdatedMaria = newresponse.body
        //We should expect the same results from the previous PUT Request.
        assertEquals(updatedMaria?.name, newupdatedMaria?.name)
        assertEquals(2, newupdatedMaria?.id)
        assertEquals(juanURI, newupdatedMaria?.href)



    }

    @Test
    fun deleteUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Delete a user
        restTemplate.execute(juanURI, HttpMethod.DELETE, {}, { assertEquals(204, it.statusCode.value()) })

        // Verify that the user has been deleted
        restTemplate.execute(juanURI, HttpMethod.GET, {}, { assertEquals(404, it.statusCode.value()) })

        //////////////////////////////////////////////////////////////////////
        // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////

        /*Not safe -> DELETE request should have changed our server state by deleting our User Juan,
        *so when we search for him we should not find him.
         */
        assertNull(addressBook.personList.find { person -> person.name == juan.name })
        /*Idempotent -> Consecutive (equal) DELETE request will always have the same
          reply from the server.
        * */
        restTemplate.execute(juanURI, HttpMethod.DELETE, {}, { assertNull(addressBook.personList.find { person -> person.name == juan.name }) })



    }

    @Test
    fun findUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val salvadorURI = URI.create("http://localhost:$port/contacts/person/1")
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Test user 1 exists
        var response = restTemplate.getForEntity(salvadorURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        var person = response.body
        assertEquals(salvador.name, person?.name)
        assertEquals(salvador.id, person?.id)
        assertEquals(salvador.href, person?.href)

        // Test user 2 exists
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        person = response.body
        assertEquals(juan.name, person?.name)
        assertEquals(juan.id, person?.id)
        assertEquals(juan.href, person?.href)

        // Test user 3 doesn't exist
        restTemplate.execute("http://localhost:$port/contacts/person/3", HttpMethod.GET, {}, { assertEquals(404, it.statusCode.value()) })
    }

    @Test
    fun HTTP2_Supp() {


        val client = HTTP2Client()
        client.start()
        // Conectamos el cliente al con el host
        val sessionPromise: FuturePromise<Session> = FuturePromise<Session>()
        client.connect(null, InetSocketAddress("localhost", port), ServerSessionListener.Adapter(), sessionPromise)

        val sesion: Session = sessionPromise.get(10, TimeUnit.SECONDS)
        // headers init
        val requestFields = HttpFields()
        requestFields.put("User-Agent", client.javaClass.name + "/" + Jetty.VERSION)
        // request http que se va a hacer y en qué version.
        val metaData = MetaData.Request("GET", HttpURI("https://localhost:$port/contacts"), HttpVersion.HTTP_2, requestFields)

        //Respuesta del serivdor, configuramos e listener para cuando responda
        var version: Int? = null
        var repuestaServer: String? = null
        val listener = object : Stream.Listener.Adapter() {
            override fun onHeaders(stream: Stream, frame: HeadersFrame) {
                //La versión debe ser http2.0
                version =  frame.metaData.httpVersion.version
            }
            override fun onData(stream: Stream, frame: DataFrame, callback: Callback) {
                val bytes = ByteArray(frame.data.remaining())
                frame.data.get(bytes)
                repuestaServer = String(bytes)
                callback.succeeded()
            }
        }
        //listen header/data/push frame
        sesion.newStream(HeadersFrame(metaData, null, true), FuturePromise(),listener)

        Thread.sleep(TimeUnit.SECONDS.toMillis(5))
        client.stop()
        //No hay nadie registrado por tanto debe devolver un array de personas vacio
        assertEquals(repuestaServer,"[]")
        //la version de la cabecera debe ser HTTP/2
        assertEquals(version,HttpVersion.HTTP_2.version)
    }

}
