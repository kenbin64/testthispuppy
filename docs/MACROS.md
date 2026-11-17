# Macros: multi-step workflows

A macro is a browser workflow written as a table, one step per row. Each `.txt` file in the macros folder (`browser.macros`, default `puppy/macros`) is one macro, and every run executes all of them.

```
action      | target          | value
open        | /customers/new  |
type        | name=name       | {dummy:name}
type        | name=email      | {dummy:email}
submit      |                 |
expect-text |                 | Saved customer
open        | /orders/new     |
select      | name=customerId | last
type        | name=product    | {dummy:text}
type        | name=quantity   | {dummy:number}
type        | name=orderDate  | {dummy:date}
submit      |                 |
expect-text |                 | Saved order
```

## Actions

| Action | Target | Value | What it does |
|---|---|---|---|
| `open` | a path, like `/orders/new` | | Goes to that page of the app |
| `type` | a field | text | Clears the field and types the value (date and number boxes are set directly) |
| `select` | a dropdown | a value, a visible label, `first` or `last` | Chooses an option; `first` and `last` skip the empty "choose one" option |
| `check` | a checkbox or radio button | | Ticks it if it is not ticked |
| `click` | a button or link | | Clicks it and waits briefly for the page to change |
| `submit` | | | Clicks the form's submit button |
| `expect-text` | | text | Fails unless the page contains the text |
| `expect-no-text` | | text | Fails if the page contains the text |
| `expect-url` | | text | Fails unless the address contains the text |
| `remember` | an element | a name | Saves the element's text as `{var:name}` for later steps |
| `no-validate` | | | Switches off the browser's own form checks, as an attacker would, so the server is what gets tested |
| `wait` | | milliseconds | Pauses (default 500) |
| `screenshot` | | a file name | Saves a picture of the page |

## Targets

| Form | Finds |
|---|---|
| `name=email` | The element with `name="email"` |
| `id=result` | The element with `id="result"` |
| `text=Save customer` | A button, link or submit input whose words are exactly "Save customer" |
| `xpath=//table//tr[2]` | An XPath expression |
| anything else | A CSS selector, like `form.checkout button.primary` |

## Values

| Placeholder | Becomes |
|---|---|
| `{dummy:name}` | A fresh Latin name, like `Dolor Tempor 3` |
| `{dummy:email}` | A fresh valid email, like `sit.amet4@example.test` |
| `{dummy:phone}` | A phone number, like `801-555-0007` |
| `{dummy:number}` | A small positive number |
| `{dummy:date}` | Today's date |
| `{dummy:text}` | A few Latin words |
| `{var:name}` | Text saved earlier with `remember` |
| `{table.column}` | That column from the first row seeded into the table |

## Three ways to create a macro

1. **Write it by hand** in the table format above. This is the most precise.
2. **Record it** by using the app. A visible Chrome opens, and every page you visit, field you fill, option you choose and button you press becomes a step. Press Enter in the terminal to finish:

   ```
   cd puppy
   mvn -q exec:java -Dexec.mainClass=com.testthispuppy.MacroRecorder -Dexec.args="http://localhost:8080 macros/my-flow.txt"
   ```

   The recorder writes the values you typed. Replace them with `{dummy:...}` placeholders to get fresh data on every run, and add `expect-text` steps where you want the macro to check something. Passwords are never recorded; they become `{dummy:text}`.
3. **Copy and adjust** an existing macro. Negative workflows are often the copy of a happy one with one wrong value, `no-validate`, and `expect-no-text` instead of `expect-text`.

## Hundreds of runs

`macro.repeat` runs every macro that many times, each with fresh dummy data:

```
macro.repeat=50
```

With the two example macros, that is 100 workflow runs. Together with the wrong-input checks (one per field per page), the injection checks (three attacks per text field, plus the API), the HTTP tests and the race test, a single command runs hundreds of tests, and the database is restored exactly at the end.

## When a macro fails

The macro stops at the failing step. The report names the step number and the step itself, and a screenshot named `<macro>-failed.png` is saved next to the macro file, showing the page as it was at that moment.
