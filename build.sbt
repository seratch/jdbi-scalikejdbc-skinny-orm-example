scalaVersion := "2.11.6"
libraryDependencies ++= Seq(
  "org.jdbi"             %  "jdbi"              % "2.59",
  "org.scalikejdbc"      %% "scalikejdbc"       % "2.2.5",
  "org.skinny-framework" %% "skinny-orm"        % "1.3.14",
  "com.h2database"       %  "h2"                % "1.4.186",
  "com.zaxxer"           %  "HikariCP"          % "2.3.3",
  "ch.qos.logback"       %  "logback-classic"   % "1.1.2"
)
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
scalariformSettings
