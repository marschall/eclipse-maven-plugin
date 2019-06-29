Eclipse Maven Plugin [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.marschall/eclipse-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.marschall/eclipse-maven-plugin)
====================

The Eclipse Plugin is used to generate Eclipse IDE files (.project, .classpath and the .settings folder) from a POM.

This is based on [wcm-io-devops/eclipse-maven-plugin](https://github.com/wcm-io-devops/eclipse-maven-plugin) which is a fork of the original [Maven Eclipse Plugin](https://maven.apache.org/plugins/maven-eclipse-plugin/) which [was retired end of 2015](http://mail-archives.apache.org/mod_mbox/maven-dev/201510.mbox/%3Cop.x55dxii1kdkhrr%40robertscholte.dynamic.ziggo.nl%3E) in favor of [M2Eclipse](https://www.eclipse.org/m2e/).

For more information check out the generated [plugin page](https://marschall.github.io/eclipse-maven-plugin/) .

Changes since the original Maven Eclipse Plugin 2.10
----------------------------------------------------

[Changelog](https://marschall.github.io/eclipse-maven-plugin/changes-report.html)

### Added
* support for test folders
* support for test dependencies, both JAR and project dependencies
* support for the `--release` compiler option
* ignore optional compile problems on attached source folders
* additional source folders
* JavaEE 7
* JavaEE 8
* Java 9 to 17


### Removed
* all goals except `eclipse` and `clean`
* AJDT support
* MyEclipse support
* RAD support



To use this in your projects update all your POMs to use

```xml
<plugin>
  <groupId>com.github.marschall</groupId>
  <artifactId>eclipse-maven-plugin</artifactId>
  <version>2.12.0</version>
</plugin>
```

instead of

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-eclipse-plugin</artifactId>
  <version>2.10</version>
</plugin>
```

If you add `com.github.marschall` as a [pluginGroup](https://maven.apache.org/guides/introduction/introduction-to-plugin-prefix-mapping.html) to your `${user.home}/.m2/settings.xml` or `${maven.home}/conf/settings.xml`

```xml
<pluginGroups>
  <pluginGroup>com.github.marschall</pluginGroup>
</pluginGroups>
```

you will be able to use `eclipse:eclipse` as a Maven goal directly.

