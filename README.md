# eddiscovery-metallicity-scan
**_CAUTION: I don't know how safe it is to query the EDD db while EDD is open. For safety, close EDD before performing any operation on it_**

Simple tools to help with the Metallicity Survey during DistantWorlds II

## edd-gasfinder.sql
This sql script attempts to find all system names that fall under the [DW2 Trans-Galactic Metallicity Survey](https://forums.frontier.co.uk/showthread.php/464051-DW2-The-Trans-Galactic-Metallicity-Survey)
It relies on the FSS Event "FSSAllBodiesFound". It selects all Systems which have a primary star of type K, G, or F.

### HowTo
Use the [SQLite Browser](https://sqlitebrowser.org/) for your OS. It is important the the tool supports Json1. The default binary for windows does so.
Feel free to use any other tool or a shell.
Open the user database in `<eddiscoverydir>\Data\EDDUser.sqlite` and run the script.

It might take a while, depending on the size of your DB. It isn't optimized for performance and no QA has been done :)

## TGMS-LOGGER - EARLY ALPHA and only for the brave :)
[![CircleCI](https://circleci.com/gh/srmo/eddiscovery-metallicity-scan/tree/develop.svg?style=svg)](https://circleci.com/gh/srmo/eddiscovery-metallicity-scan/tree/develop)

Java tool to scan the e:d commander's log for scanned systems.
Currently provides a rudimentary UI that will output the FGK systems that have a full scan.

**_The tool will store data in your user directory in a folder called `tgms-logger` It currently only contains a small property file that
 stores the folder you select for scanning_** 

### HowTo 
Either:
* download the last zip found in the release tab **_WINDOWS ONLY_**
* unzip to anywhere
* run the file `tgms-logger.bat` in `bin/` directory

OR
* checkout the code
* cd into `tgms-logger`
* invoke `gradlew run`

### TODO
* add build description
* add tests
* add documentation
* add functionality to:
    * export system names to file
    * export system names to clipboard
    * select which systems to export
    * maybe a table view?
    * persist which logfile was scanned last
