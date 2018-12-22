package github.vrih.xsub.domain


import org.junit.Before

class ArtistTest {

    private lateinit var subject: Artist;

    @Before
    fun setUp(){
        subject = Artist("ID", "Name")
        subject.rating = 5
    }
}