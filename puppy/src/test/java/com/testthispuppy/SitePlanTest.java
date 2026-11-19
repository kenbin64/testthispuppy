package com.testthispuppy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The site mapper's plan: guesses, generated workflows, the live-site guard, and the database check's parsing. */
class SitePlanTest {
    private static final SiteMapper.Form MEMBER_FORM = new SiteMapper.Form("http://shop.test/members/new", "http://shop.test/members", "post", List.of(
            new SiteMapper.Field("fullName", "input", "text", true, "", "", List.of(), "Name"),
            new SiteMapper.Field("email", "input", "email", true, "", "", List.of(), "Email"),
            new SiteMapper.Field("quantity", "input", "number", false, "1", "", List.of(), ""),
            new SiteMapper.Field("plan", "select", "select-one", false, "", "", List.of("basic", "pro"), ""),
            new SiteMapper.Field("password", "input", "password", true, "", "", List.of(), "")), "Join");

    @Test
    void guessesTablesAndColumnsFromTheForm() {
        assertEquals("member", PlanWriter.tableGuess("http://shop.test/members"));
        assertEquals("category", PlanWriter.tableGuess("http://shop.test/api/v1/categories/new"));
        assertEquals("order", PlanWriter.tableGuess("http://shop.test/orders/42/edit"));
        assertEquals("first_name", PlanWriter.columnGuess("firstName"));
        assertEquals("email", PlanWriter.keyField(MEMBER_FORM).name(), "an email is the best way to find the saved row");
    }

    @Test
    void aGeneratedWorkflowFillsTheFormAndLeavesTheGuessesToAPerson() {
        String macro = PlanWriter.macro(MEMBER_FORM);
        assertTrue(macro.contains("open           | /members/new"));
        assertTrue(macro.contains("type           | name=fullName         | {dummy:name}"));
        assertTrue(macro.contains("type           | name=email            | {dummy:email}"));
        assertTrue(macro.contains("type           | name=quantity         | 1"), "a number with a minimum uses the minimum");
        assertTrue(macro.contains("select         | name=plan             | first"));
        assertTrue(macro.contains("name=password         | " + PlanWriter.TEST_PASSWORD), "a sign-up form gets the fixed test password");
        SiteMapper.Form signIn = new SiteMapper.Form("http://shop.test/login", "http://shop.test/login", "post", List.of(
                new SiteMapper.Field("email", "input", "email", true, "", "", List.of(), ""),
                new SiteMapper.Field("password", "input", "password", true, "", "", List.of(), "")), "Sign in");
        assertTrue(signIn.signIn(), "email and password alone is a sign-in");
        assertFalse(MEMBER_FORM.signIn(), "name, email, password and more is a sign-up: a form that writes");
        assertTrue(macro.contains("# expect-row    | member                | email={typed:email}"), "the database check is there, switched off until confirmed");
    }

    @Test
    void theGuardRefusesTheLiveSiteButNotACopy(@TempDir Path folder) throws Exception {
        Properties p = new Properties();
        p.setProperty("guard.liveUrl", "https://www.shop.test");
        p.setProperty("app.baseUrl", "https://shop.test/");
        assertTrue(SiteMapper.guardRefusal(PuppyConfig.of(p, folder)).startsWith("REFUSED"), "www. or not, it is the live site");
        p.setProperty("app.baseUrl", "https://staging.shop.test");
        assertNull(SiteMapper.guardRefusal(PuppyConfig.of(p, folder)));
        p.setProperty("guard.liveUrl", "http://localhost:8082");
        p.setProperty("app.baseUrl", "http://localhost:8083");
        assertNull(SiteMapper.guardRefusal(PuppyConfig.of(p, folder)), "a copy on another port of the same machine is a different place");
        p.setProperty("app.baseUrl", "http://localhost:8082/");
        assertTrue(SiteMapper.guardRefusal(PuppyConfig.of(p, folder)).startsWith("REFUSED"));
    }

    @Test
    void onlyPagesAreFollowed() {
        assertTrue(SiteMapper.isPage("https://shop.test/members/new"));
        assertTrue(SiteMapper.isPage("https://shop.test/about.html"));
        assertFalse(SiteMapper.isPage("https://shop.test/brochure.pdf"));
        assertFalse(SiteMapper.isPage("https://shop.test/img/logo.png"));
        assertEquals("https://shop.test/a", SiteMapper.withoutFragment("https://shop.test/a#top"));
    }

    @Test
    void theDatabaseCheckReadsColumnValuePairs() {
        List<String[]> pairs = MacroRunner.parseWhere("email=a@b.test, name=Lorem Ipsum, note=x=y");
        assertEquals(3, pairs.size());
        assertEquals("email", pairs.get(0)[0]);
        assertEquals("Lorem Ipsum", pairs.get(1)[1]);
        assertEquals("x=y", pairs.get(2)[1], "a value may contain =");
        assertThrows(IllegalArgumentException.class, () -> MacroRunner.parseWhere("no pairs here"));
    }

    @Test
    void writesAPlanFolderFromAMap(@TempDir Path folder) throws Exception {
        SiteMapper.Form search = new SiteMapper.Form("http://shop.test/", "http://shop.test/search", "get",
                List.of(new SiteMapper.Field("q", "input", "search", false, "", "", List.of(), "")), "Go");
        List<SiteMapper.Page> pages = List.of(new SiteMapper.Page("http://shop.test/", "Shop", List.of(search)),
                new SiteMapper.Page("http://shop.test/members/new", "Join", List.of(MEMBER_FORM)));
        PlanWriter.write(folder, "http://shop.test", "http://localhost:8090", pages);
        String settings = Files.readString(folder.resolve("plan.properties"));
        assertTrue(settings.contains("guard.liveUrl=http://shop.test") && settings.contains("app.baseUrl=http://localhost:8090"));
        assertTrue(settings.contains("xss.paths=/search?q="), "the search becomes a cross-site scripting target");
        assertTrue(Files.exists(folder.resolve("macros/members-new-to-members.txt")));
        assertTrue(Files.readString(folder.resolve("PLAN.md")).contains("Likely a row in `member`"));
        assertEquals(1, GridFile.read(folder.resolve("pages.txt")).size(), "one page has a form that writes");
    }
}
