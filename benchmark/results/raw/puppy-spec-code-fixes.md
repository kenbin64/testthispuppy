# Code fix list

Source: ../bugzoo  
Checks: syntax, structure, bugs, practices, security (standard)  
Found: 20 issue(s)

How to use this list: fix each item at its file and line without changing what the code does, most serious first, then run TestThisPuppy again (its tests and the test-the-tests check show whether behaviour changed). An item that is right as it stands can be left, with a comment saying why.

## src/main/java/com/bugzoo/Member.java

- **line 11**, medium, structure: DataClass. The class 'Member' is suspected to be a Data Class (WOC=12.500%, NOPA=0, NOAM=7, WMC=8)
  - Why: Data Classes are simple data holders, which reveal most of their state, and without complex functionality. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#dataclass))
- **line 29**, medium, bugs: OverrideBothEqualsAndHashcode. Ensure you override both equals() and hashCode()
  - Why: Override both `public boolean Object.equals(Object other)` and `public int Object.hashCode()` or override neither. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#overridebothequalsandhashcode))

## src/main/java/com/bugzoo/MemberRepository.java

- **line 6**, medium-high, practices: ImplicitFunctionalInterface. Annotate this interface with @FunctionalInterface or with @SuppressWarnings("PMD.ImplicitFunctionalInterface") to clarify your intent.
  - Why: Reports functional interfaces that were not explicitly declared as such with the annotation `@FunctionalInterface`. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_bestpractices.html#implicitfunctionalinterface))

## src/main/java/com/bugzoo/PurchaseOrder.java

- **line 14**, medium, structure: DataClass. The class 'PurchaseOrder' is suspected to be a Data Class (WOC=0.000%, NOPA=0, NOAM=9, WMC=9)
  - Why: Data Classes are simple data holders, which reveal most of their state, and without complex functionality. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_design.html#dataclass))

## src/main/java/com/bugzoo/ReportExporter.java

- **line 31**, high, practices: AvoidFileStream. Avoid instantiating FileInputStream, FileOutputStream, FileReader, or FileWriter
  - Why: The FileInputStream and FileOutputStream classes contains a finalizer method which will cause garbage collection pauses. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_performance.html#avoidfilestream))
- **line 16**, medium, bugs: SimpleDateFormatNeedsLocale. When instantiating a SimpleDateFormat object, specify a Locale
  - Why: Be sure to specify a Locale when creating SimpleDateFormat instances to ensure that locale-appropriate formatting is used. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#simpledateformatneedslocale))
- **line 20**, medium, bugs: CompareObjectsWithEquals. Use equals() to compare object references.
  - Why: Use `equals()` to compare object references; avoid comparing them with `==`. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#compareobjectswithequals))
- **line 20**, medium, bugs: UseEqualsToCompareStrings. Use equals() to compare strings instead of '==' or '!='
  - Why: Using '==' or '!=' to compare strings is only reliable if the interned string (`String#intern()`) is used on both sides. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#useequalstocomparestrings))
- **line 20**, medium, bugs: AvoidLiteralsInIfCondition. Avoid using literals such as "csv" in if statements
  - Why: Avoid using hard-coded literals in conditional statements. ([rule](https://docs.pmd-code.org/pmd-doc-7.29.0/pmd_rules_java_errorprone.html#avoidliteralsinifcondition))
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
