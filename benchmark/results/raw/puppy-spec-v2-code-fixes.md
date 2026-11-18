# Code fix list

Source: ../bugzoo  
Checks: syntax, structure, bugs, practices, security, libraries (standard)  
Found: 48 issue(s)

How to use this list: fix each item at its file and line without changing what the code does, most serious first, then run TestThisPuppy again (its tests and the test-the-tests check show whether behaviour changed). An item that is right as it stands can be left, with a comment saying why.

## pom.xml

- **whole file**, high, libraries: VulnerableLibrary. org.apache.commons:commons-text 1.9: 1 known vulnerability (CVE-2022-42889)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-599f-7c49-w659))
- **whole file**, high, libraries: VulnerableLibrary. org.apache.tomcat.embed:tomcat-embed-core 10.1.41: 29 known vulnerabilities (CVE-2025-55668, CVE-2025-53506, CVE-2025-49124, CVE-2026-24880, CVE-2026-43515, CVE-2026-43513 and 23 more)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-23hv-mwm6-g8jf))
- **whole file**, high, libraries: VulnerableLibrary. org.springframework:spring-webmvc 6.2.7: 14 known vulnerabilities (CVE-2026-41845, CVE-2026-22737, CVE-2026-22735, CVE-2026-22745, CVE-2026-41843, CVE-2026-41846 and 8 more)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-3chg-m5w7-qfv5))
- **whole file**, high, libraries: VulnerableLibrary. org.thymeleaf:thymeleaf 3.1.3.RELEASE: 3 known vulnerabilities (CVE-2026-41901, CVE-2026-40477, CVE-2026-40478)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-c9ph-gxww-7744))
- **whole file**, high, libraries: VulnerableLibrary. org.thymeleaf:thymeleaf-spring6 3.1.3.RELEASE: 3 known vulnerabilities (CVE-2026-41901, CVE-2026-40477, CVE-2026-40478)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-c9ph-gxww-7744))
- **whole file**, medium-high, libraries: VulnerableLibrary. com.fasterxml.jackson.core:jackson-core 2.19.0: 4 known vulnerabilities (CVE-2026-18401, CVE-2026-89425, CVE-2026-89407, CVE-2026-68494)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-72hv-8253-57qq))
- **whole file**, medium-high, libraries: VulnerableLibrary. com.fasterxml.jackson.core:jackson-databind 2.19.0: 12 known vulnerabilities (CVE-2026-59888, CVE-2026-59889, CVE-2026-54515, CVE-2026-91777, CVE-2026-83557, CVE-2026-54514 and 6 more)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-3pjw-73gf-8qr5))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.assertj:assertj-core 3.27.3: 1 known vulnerability (CVE-2026-24400)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-rqfh-9r24-8c9r))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.postgresql:postgresql 42.7.5: 3 known vulnerabilities (CVE-2026-42198, CVE-2025-49146, CVE-2026-54291)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-98qh-xjc8-98pq))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.springframework.boot:spring-boot 3.5.0: 1 known vulnerability (CVE-2026-40973)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-wwpq-f5c3-7hvx))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.springframework.data:spring-data-commons 3.5.0: 4 known vulnerabilities (CVE-2026-41721, CVE-2026-41711, CVE-2026-41695, CVE-2026-41716)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-5m4m-73w9-8433))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.springframework:spring-core 6.2.7: 2 known vulnerabilities (CVE-2026-41848, CVE-2025-41249)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-659m-px2c-25wj))
- **whole file**, medium-high, libraries: VulnerableLibrary. org.springframework:spring-expression 6.2.7: 3 known vulnerabilities (CVE-2026-41852, CVE-2026-41850, CVE-2026-41851)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-9f52-rjqv-25qv))
- **whole file**, medium, libraries: VulnerableLibrary. ch.qos.logback:logback-core 1.5.18: 4 known vulnerabilities (CVE-2025-11226, CVE-2026-10532, CVE-2026-9828, CVE-2026-1225)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-25qh-j22f-pwp8))
- **whole file**, medium, libraries: VulnerableLibrary. org.apache.commons:commons-lang3 3.17.0: 1 known vulnerability (CVE-2025-48924)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-j288-q9x7-2f5v))
- **whole file**, medium, libraries: VulnerableLibrary. org.apache.logging.log4j:log4j-api 2.24.3: 1 known vulnerability (CVE-2026-49844)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-qv9r-c865-cp47))
- **whole file**, medium, libraries: VulnerableLibrary. org.springframework.boot:spring-boot-autoconfigure 3.5.0: 1 known vulnerability (CVE-2026-41001)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-ggg2-9786-hwc8))
- **whole file**, medium, libraries: VulnerableLibrary. org.springframework:spring-web 6.2.7: 2 known vulnerabilities (CVE-2025-41234, CVE-2026-41854)
  - Why: A library with known vulnerabilities can let an attacker in through code you did not write; upgrade it to a fixed version. ([rule](https://osv.dev/GHSA-6r3c-xf4w-jxjm))

## src/main/java/com/bugzoo/ApiController.java

- **line 35**, medium, security: ENTITY_LEAK. Unexpected property could be leaked because a persistence class is directly exposed to the client
  - Why: Unexpected property leak (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#entity-leak))
- **line 40**, medium, security: ENTITY_LEAK. Unexpected property could be leaked because a persistence class is directly exposed to the client
  - Why: Unexpected property leak (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#entity-leak))
- **line 45**, medium, security: ENTITY_LEAK. Unexpected property could be leaked because a persistence class is directly exposed to the client
  - Why: Unexpected property leak (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#entity-leak))
- **line 45**, medium, security: ENTITY_MASS_ASSIGNMENT. Persistent objects as MVC parameter could allow attacker to set fields that the developer never intended to be set via request parameters.
  - Why: Mass assignment (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#entity-mass-assignment))
- **line 54**, medium, security: SQL_INJECTION_SPRING_JDBC. This use of org/springframework/jdbc/core/JdbcTemplate.queryForList(Ljava/lang/String;)Ljava/util/List; can be vulnerable to SQL injection (with Spring JDBC)
  - Why: Potential JDBC Injection (Spring JDBC) (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#sql-injection-spring-jdbc))
- **line 59**, medium, security: ENTITY_LEAK. Unexpected property could be leaked because a persistence class is directly exposed to the client
  - Why: Unexpected property leak (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#entity-leak))

## src/main/java/com/bugzoo/Member.java

- **line 11**, medium, structure: DataClass. The class 'Member' is suspected to be a Data Class (WOC=12.500%, NOPA=0, NOAM=7, WMC=8)
  - Why: Data Classes are simple data holders, which reveal most of their state, and without complex functionality. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#dataclass))
- **line 29**, medium, bugs: OverrideBothEqualsAndHashcode. Ensure you override both equals() and hashCode()
  - Why: Override both `public boolean Object.equals(Object other)` and `public int Object.hashCode()` or override neither. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#overridebothequalsandhashcode))
- **line 30**, medium, practices: HE_EQUALS_USE_HASHCODE. com.bugzoo.Member defines equals and uses Object.hashCode()
  - Why: Class defines equals() and uses Object.hashCode() (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#he-equals-use-hashcode))

## src/main/java/com/bugzoo/MemberRepository.java

- **line 6**, medium-high, practices: ImplicitFunctionalInterface. Annotate this interface with @FunctionalInterface or with @SuppressWarnings("PMD.ImplicitFunctionalInterface") to clarify your intent.
  - Why: Reports functional interfaces that were not explicitly declared as such with the annotation `@FunctionalInterface`. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_bestpractices.html#implicitfunctionalinterface))

## src/main/java/com/bugzoo/PurchaseOrder.java

- **line 14**, medium, structure: DataClass. The class 'PurchaseOrder' is suspected to be a Data Class (WOC=0.000%, NOPA=0, NOAM=9, WMC=9)
  - Why: Data Classes are simple data holders, which reveal most of their state, and without complex functionality. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#dataclass))

## src/main/java/com/bugzoo/ReportExporter.java

- **line 31**, high, practices: AvoidFileStream. Avoid instantiating FileInputStream, FileOutputStream, FileReader, or FileWriter
  - Why: The FileInputStream and FileOutputStream classes contains a finalizer method which will cause garbage collection pauses. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_performance.html#avoidfilestream))
- **line 36**, medium-high, bugs: STCAL_INVOKE_ON_STATIC_DATE_FORMAT_INSTANCE. Call to method of static java.text.DateFormat in com.bugzoo.ReportExporter.fill(String, Map)
  - Why: Call to static DateFormat (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#stcal-invoke-on-static-date-format-instance))
- **line 49**, medium-high, bugs: RV_ABSOLUTE_VALUE_OF_HASHCODE. Bad attempt to compute absolute value of signed 32-bit hashcode in com.bugzoo.ReportExporter.bucket(String, int)
  - Why: Bad attempt to compute absolute value of signed 32-bit hashcode (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#rv-absolute-value-of-hashcode))
- **line 16**, medium, bugs: SimpleDateFormatNeedsLocale. When instantiating a SimpleDateFormat object, specify a Locale
  - Why: Be sure to specify a Locale when creating SimpleDateFormat instances to ensure that locale-appropriate formatting is used. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#simpledateformatneedslocale))
- **line 20**, medium, bugs: CompareObjectsWithEquals. Use equals() to compare object references.
  - Why: Use `equals()` to compare object references; avoid comparing them with `==`. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#compareobjectswithequals))
- **line 20**, medium, bugs: UseEqualsToCompareStrings. Use equals() to compare strings instead of '==' or '!='
  - Why: Using '==' or '!=' to compare strings is only reliable if the interned string (`String#intern()`) is used on both sides. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#useequalstocomparestrings))
- **line 20**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as "csv" in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
- **line 20**, medium, practices: ES_COMPARING_PARAMETER_STRING_WITH_EQ. Comparison of String parameter using == or != in com.bugzoo.ReportExporter.header(String)
  - Why: Comparison of String parameter using == or != (SpotBugs) ([rule](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#es-comparing-parameter-string-with-eq))
- **line 31**, medium, bugs: CloseResource. Ensure that resources like this BufferedReader object are closed after use
  - Why: Ensure that resources (like `java.sql.Connection`, `java.sql.Statement`, and `java.sql.ResultSet` objects and any subtype of `java.lang.AutoCloseable`) are always closed after use. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#closeresource))
- **line 31**, medium, practices: RelianceOnDefaultCharset. Specify a character set instead of relying on the default charset
  - Why: Be sure to specify a character set for APIs that use the JVM's default character set to ensure stable encoding behavior between different JVMs, programs, and servers. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_bestpractices.html#relianceondefaultcharset))
- **line 36**, medium, bugs: UnsynchronizedStaticFormatter. Static Formatter objects should be accessed in a synchronized manner
  - Why: Instances of `java.text.Format` are generally not synchronized. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_multithreading.html#unsynchronizedstaticformatter))
- **line 36**, medium, bugs: ReplaceJavaUtilDate. Usage of java.util.Date should be replaced with classes from java.time
  - Why: The legacy `java.util.Date` class is mutable, not thread-safe, and has a confusing API. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#replacejavautildate))

## src/main/java/com/bugzoo/ShippingRules.java

- **line 8**, medium, structure: CognitiveComplexity. The method 'shippingDays(String, int, boolean, boolean, boolean, int)' has a cognitive complexity of 67, current threshold is 15
  - Why: Methods that are highly complex are difficult to read and more costly to maintain. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#cognitivecomplexity))
- **line 8**, medium, structure: CyclomaticComplexity. The method 'shippingDays(String, int, boolean, boolean, boolean, int)' has a cyclomatic complexity of 29.
  - Why: The complexity of methods directly affects maintenance costs and readability. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#cyclomaticcomplexity))
- **line 10**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as "north" in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
- **line 13**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as 10 in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
- **line 18**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as "south" in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
- **line 21**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as 20 in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
- **line 26**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as "island" in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
