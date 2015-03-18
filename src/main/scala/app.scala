import com.zaxxer.hikari._
import scalikejdbc._

object DataSource {
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

object SettingsInitializer {
  lazy val init = {
    Class.forName("org.h2.Driver")
    ConnectionPool.singleton(new DataSourceConnectionPool(DataSource()))
    DB.autoCommit { implicit s =>
      sql"create table application (id serial not null, name varchar(500))".execute.apply()
      val name = "sample"
      sql"insert into application (name) values ($name)".update.apply()
    }
  }
}

trait Settings {
  SettingsInitializer.init
}

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
  using(dbi.open()) { h =>
    val result = h.createQuery("select id, name from application").map(new DefaultMapper).iterator().asScala
    result.foreach(println)

    val dao = dbi.open(classOf[ApplicationDAO])
    val app = dao.finbByName("sample")
    println(app)
    val app2 = dao.finbByBean(app)
    println(app2)
  }
}

object ScalikeJDBCExample extends App with Settings {

  case class Application(id: Int, name: String)
  object Application extends SQLSyntaxSupport[Application] {
    def extract(rs: WrappedResultSet) = new Application(rs.get("id"), rs.get("name"))
    def findByName(name: String): Option[Application] = DB.readOnly { implicit s =>
      sql"select id, name from application where name = $name".map(extract).single.apply()
    }
  }

  DB.readOnly { implicit s =>
    val result = sql"select * from application".toMap.list.apply()
    result.foreach(println)
    val app = Application.findByName("sample")
    app.foreach(println)
  }
}

object SkinnyORMExample extends App with Settings {
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
