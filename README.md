# Maven Modules

A tool to analyze dependencies between modules within a maven project.

## Introduction

The following commands are available:

- `top-level-modules`: List all 'top-level' modules, i.e. modules that are not
  depended on by any other module within this project.
- `delete-modules`: Given a list of 'top-level' modules to delete, list all
  modules that can be deleted.
- `subgraph-dot`: Visualize the subgraph of a given module with the `graphviz`
  `dot` command.
- `module-source-files`: List all modules with and ordered by the number of
  files in `src/main/`.
- `module-dependency-count`: List all modules with and ordered by the number
  of incoming references. 

## Building a debian package

Before being able to build a debian image, you need to run:

    sudo apt install debhelper javahelper

Then

    ./gradlew deb
