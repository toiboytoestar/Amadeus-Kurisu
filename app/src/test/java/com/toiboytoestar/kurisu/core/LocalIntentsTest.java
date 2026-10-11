package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalIntentsTest {

    private LocalIntents.Intent p(String s) {
        LocalIntents.Intent i = LocalIntents.parse(s);
        assertNotNull("expected an intent for: " + s, i);
        return i;
    }

    @Test
    public void timers() {
        assertEquals(600, p("Set a timer for 10 minutes").seconds);
        assertEquals(LocalIntents.Type.TIMER, p("set a timer for ten minutes").type);
        assertEquals(600, p("set a timer for ten minutes").seconds);
        assertEquals(90, p("Kurisu, please start a 90 second timer").seconds);
        assertEquals(3600, p("timer for an hour").seconds);
        assertEquals(5400, p("Set a timer for one and a half hours").seconds);
        assertEquals(2700, p("start a timer for forty five minutes").seconds);
        assertEquals(1800, p("set a timer for half an hour").seconds);
        assertEquals(LocalIntents.Type.TIMER, p("wake me up in 20 minutes").type);
    }

    @Test
    public void alarms() {
        LocalIntents.Intent a = p("Set an alarm for 6:30 am");
        assertEquals(LocalIntents.Type.ALARM, a.type);
        assertEquals(6, a.hour);
        assertEquals(30, a.minute);
        a = p("wake me up at 7");
        assertEquals(7, a.hour);
        assertEquals(0, a.minute);
        a = p("set an alarm for 6 pm");
        assertEquals(18, a.hour);
        a = p("set alarm at 18:45");
        assertEquals(18, a.hour);
        assertEquals(45, a.minute);
        a = p("set an alarm for 12 am");
        assertEquals(0, a.hour);
    }

    @Test
    public void reminders() {
        LocalIntents.Intent r = p("Remind me to call mom in 2 hours");
        assertEquals(LocalIntents.Type.REMINDER, r.type);
        assertEquals(7200, r.seconds);
        assertEquals("Call mom", r.text);
        r = p("remind me in 15 minutes to take the laundry out");
        assertEquals(900, r.seconds);
        assertEquals("Take the laundry out", r.text);
        r = p("remind me at 5:30 pm to submit the report");
        assertEquals(17, r.hour);
        assertEquals(30, r.minute);
        assertEquals("Submit the report", r.text);
        r = p("remind me to water the plants at 8 pm");
        assertEquals(20, r.hour);
        assertEquals("Water the plants", r.text);
        // no time: let the language model ask
        assertNull(LocalIntents.parse("remind me to buy milk"));
    }

    @Test
    public void tasksNotesMemory() {
        LocalIntents.Intent t = p("Add a task to finish the physics homework");
        assertEquals(LocalIntents.Type.TASK, t.type);
        assertEquals("Finish the physics homework", t.text);
        t = p("add buy printer paper to my to-do list");
        assertEquals("Buy printer paper", t.text);
        LocalIntents.Intent n = p("Take a note that the lab meeting moved to Thursday");
        assertEquals(LocalIntents.Type.NOTE, n.type);
        assertEquals("The lab meeting moved to Thursday", n.text);
        LocalIntents.Intent m = p("Remember that my exam is on Friday");
        assertEquals(LocalIntents.Type.MEMORY, m.type);
        assertEquals("My exam is on Friday", m.text);
    }

    @Test
    public void appsSearchAndSystem() {
        assertEquals("camera", p("open the camera app").text);
        assertEquals(LocalIntents.Type.OPEN_APP, p("Open Chrome").type);
        assertEquals("quantum entanglement", p("search the web for quantum entanglement").text);
        assertEquals(LocalIntents.Type.TIME, p("What time is it?").type);
        assertEquals(LocalIntents.Type.DATE, p("what's the date today").type);
        assertEquals(LocalIntents.Type.STOP, p("stop").type);
        assertEquals(LocalIntents.Type.NEW_CHAT, p("new chat").type);
        assertEquals(LocalIntents.Type.POMODORO, p("start a pomodoro").type);
        assertEquals(LocalIntents.Type.POMODORO, p("begin a focus session").type);
        assertEquals("cards", p("review my flashcards").text);
        assertEquals("notes", p("show my notes").text);
    }

    @Test
    public void ordinaryConversationFallsThrough() {
        assertNull(LocalIntents.parse("What is the thalamus?"));
        assertNull(LocalIntents.parse("Explain how a timer circuit works in a 555 chip"));
        assertNull(LocalIntents.parse("I have an exam at 9 tomorrow and I'm nervous"));
        assertNull(LocalIntents.parse("open a debate about free will"));
        assertNull(LocalIntents.parse(""));
        assertNull(LocalIntents.parse("What do you think about time travel?"));
    }
}
