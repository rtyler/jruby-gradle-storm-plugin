package com.github.jrubygradle.storm

import com.github.jrubygradle.JRubyPlugin

import groovy.transform.PackageScope

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/**
 * @author R. Tyler Croy
 */
class JRubyStormPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin : 'com.github.jruby-gradle.base'
        project.apply plugin : 'com.github.jruby-gradle.jar'
        project.apply plugin : 'com.github.johnrengelman.shadow'

        project.configurations.maybeCreate('jrubyStorm')
        project.configurations.maybeCreate('jrubyStormLocal')

        project.configurations {
          jrubyStormLocal.extendsFrom jrubyStorm
        }


        project.task('jrubyStorm', type: ShadowJar) {
            group JRubyPlugin.TASK_GROUP_NAME
            description 'Create a JRuby-based Storm topology'
            dependsOn project.tasks.jrubyPrepare
            exclude '*.sw*', '*.gitkeep', '*.md', 'META-INF/BCKEY*'

            into('topologies') {
              from 'topologies'
            }
            into('bolts') {
              from 'bolts'
            }

            jruby {
                // Use the default GEM installation directory
                defaultGems()
                mainClass 'redstorm.TopologyLauncher'
            }
        }

        updateRepositories(project)
        updateDependencies(project)

        project.afterEvaluate {
          JRubyStormLocal.updateDependencies(project)

          // Add the jrubyStorm configuration to the jrubyStorm task to make
          // sure ShadowJar will unzip/incorporate the right data unpacked into
          // the artifact
          project.tasks.jrubyStorm.configurations.add(project.configurations.getByName('jrubyStorm'))
        }
    }

    @PackageScope
    void updateRepositories(Project project) {
      project.repositories {
        // jcenter contains the redstorm and gradle dependencies
        jcenter()
        // Repositories for Storm dependencies
        mavenCentral()
        maven { url 'http://clojars.org/repo/' }
        maven { url 'http://conjars.org/repo/' }
      }
    }

    @PackageScope
    void updateDependencies(Project project) {
      project.dependencies {
        // Excluding storm-core for the configuration where we create the
        // topology jar. This is because the running storm cluster will provide
        // the classes from this dependency. If we attempt to includ ethis, the
        // storm classes will not initialize properly and you'll get exceptions
        // like: "cannot load or initialize class backtype.storm.LocalCluster
        jrubyStorm (group: 'com.github.jruby-gradle',
                name: 'redstorm',
                version: '0.7.1+') {
            exclude module: 'storm-core'
        }

        // We must pull in bouncycastle since it's not included as a proper
        // dependency for the jruby-complete.jar that gets bundled into the
        // topology jar. Similar code exists in the base plugin for the
        // JRubyExec task
        jrubyStorm "org.bouncycastle:bcprov-jdk15on:${project.jruby.bouncycastleVersion}"

        jrubyStormLocal group: 'org.apache.storm',
                name: 'storm-core',
                version: '0.9.2-incubating'
      }
    }
}
