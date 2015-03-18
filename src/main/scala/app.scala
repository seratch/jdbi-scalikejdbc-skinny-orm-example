/**
 * HikariCP DataSource.
 */
object DataSource {

  import com.zaxxer.hikari._

  private[this] lazy val instance = {
    val config = new HikariConfig()
    config.setJdbcUrl("jdbc:h2:mem:hello;MODE=PostgreSQL")
    config.setUsername("user")
    config.setPassword("pass")
    config.addDataSourceProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    new HikariDataSource(config)
  }
  def apply(): javax.sql.DataSource = instance
}

/**
 * Settings initializer.
 */
object SettingsInitializer {

  import scalikejdbc._

  lazy val init = {
    Class.forName("org.h2.Driver")
    // ScalikeJDBC connection pool - http://scalikejdbc.org/documentation/connection-pool.html
    ConnectionPool.singleton(new DataSourceConnectionPool(DataSource()))
    DB.autoCommit { implicit s =>
      sql"create table application (id serial not null, name varchar(500))".execute.apply()
      val name = "sample"
      sql"insert into application (name) values ($name)".update.apply()
    }
  }
}

/**
 * Initialize CP settings and load tables.
 */
trait Settings {
  SettingsInitializer.init
}

/**
 * JDBI Exmaple - http://jdbi.org/
 */
object JDBIExample extends App with Settings {

  import java.sql.ResultSet
  import org.skife.jdbi.v2._
  import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper
  import org.skife.jdbi.v2.sqlobject._
  import org.skife.jdbi.v2.tweak.ResultSetMapper
  import scala.beans.BeanProperty
  import scala.collection.JavaConverters._

  @RegisterMapper(Array(classOf[ApplicationMapper]))
  trait ApplicationDAO {
    @SqlQuery("select * from application where name = :name")
    def finbByName(@Bind("name") name: String): Application
    @SqlQuery("select * from application where id = :id")
    def finbByBean(@BindBean app: Application): Application
  }

  case class Application(@BeanProperty id: String, @BeanProperty name: String)
  class ApplicationMapper extends ResultSetMapper[Application] {
    override def map(index: Int, r: ResultSet, ctx: StatementContext): Application = {
      Application(r.getString("id"), r.getString("name"))
    }
  }

  val dbi = new DBI(DataSource())
  val h = dbi.open()
  val result = h.createQuery("select id, name from application").map(new DefaultMapper).iterator().asScala
  result.foreach(println)
  h.close()

  val dao = dbi.open(classOf[ApplicationDAO])
  val app = dao.finbByName("sample")
  println(app)
  val app2 = dao.finbByBean(app)
  println(app2)
}

/**
 * ScalikeJDBC Exmaple - http://scalikejdbc.org/
 */
object ScalikeJDBCExample extends App with Settings {

  import scalikejdbc._

  case class Application(id: Int, name: String)
  object Application extends SQLSyntaxSupport[Application] {
    def extract(rs: WrappedResultSet) = new Application(rs.get("id"), rs.get("name"))
    def findByName(name: String): Option[Application] = DB.readOnly { implicit s =>
      sql"select id, name from application where name = $name".map(extract).single.apply()
    }
  }

  DB.readOnly { implicit s =>
    val result = sql"select id, name from application".toMap.list.apply()
    result.foreach(println)

    val app = Application.findByName("sample")
    app.foreach(println)
  }
}

/**
 * Skinny ORM Example - http://skinny-framework.org/documentation/orm.html
 */
object SkinnyORMExample extends App with Settings {

  import scalikejdbc._
  import skinny.orm._

  case class Application(id: Int, name: String)
  object Application extends SkinnyCRUDMapper[Application] {
    override lazy val defaultAlias = createAlias("a")
    override def extract(rs: WrappedResultSet, n: ResultName[Application]) = autoConstruct(rs, n)
  }

  DB.readOnly { implicit s =>
    val result = sql"select id, name from application".toMap.list.apply()
    result.foreach(println)

    val apps: Seq[Application] = Application.where('name -> "sample").apply()
    apps.foreach(println)

    val a = Application.defaultAlias
    val app: Option[Application] = Application.findBy(sqls.eq(a.name, "sample"))
    app.foreach(println)
  }
}
