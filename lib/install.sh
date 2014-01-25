#!/bin/sh
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=gui/ICOReader-1.04.jar -DgroupId=nl.ikarus.nxt.priv.imageio -DartifactId=icoreader -Dversion=1.0.4 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=jic/java-icon.jar -DgroupId=com.ikayzo.swing -DartifactId=icon -Dversion=1.0.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=salamander/svgSalamander.jar -DgroupId=com.kitfox.svg -DartifactId=svgsalamander -Dversion=1.0.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=hermit/HermiT.jar -DgroupId=com.hermit-reasoner -DartifactId=org.semanticweb.hermit -Dversion=1.3.1 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=minilearning/minilearningbr.jar -DgroupId=org.opensat -DartifactId=minisat -Dversion=1.0.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=orbital/orbital-core.jar -DgroupId=orbital -DartifactId=core -Dversion=1.3.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=orbital/orbital-ext.jar -DgroupId=orbital -DartifactId=ext -Dversion=1.3.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=owlapi/owlapi-bin.jar -DgroupId=org.semanticweb.owlapi -DartifactId=owlapi -Dversion=3.2.4 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:2.5.1:install-file -Dfile=skosapi/skosapi.jar -DgroupId=org.semanticweb.skosapi -DartifactId=skosapi -Dversion=1.0.0 -Dpackaging=jar
