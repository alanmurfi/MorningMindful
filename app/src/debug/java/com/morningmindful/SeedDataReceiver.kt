package com.morningmindful

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.morningmindful.data.entity.JournalEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only broadcast receiver to seed journal data.
 * Trigger with: adb shell am broadcast -a com.morningmindful.SEED_DATA -n com.morningmindful/.SeedDataReceiver
 */
class SeedDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("SeedData", "Seeding 10 days of journal entries...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = MorningMindfulApp.getInstance()
                val repo = app.journalRepository
                val today = LocalDate.now()

                val entries = listOf(
                    JournalEntry(
                        date = today,
                        content = "Today I woke up before my alarm and it felt like a small victory. The morning light was filtering through the curtains and instead of reaching for my phone, I just lay there for a moment, appreciating the quiet. Made myself a proper breakfast - scrambled eggs and toast with that good sourdough from the farmers market. I've been thinking about how much my mornings have changed since I started this journaling habit. There's something powerful about putting thoughts on paper before the noise of the day begins. I have a big meeting this afternoon but I'm not anxious about it. Writing these words helps me feel grounded and ready to face whatever comes.",
                        wordCount = 119,
                        mood = "\uD83D\uDE0A",
                        createdAt = dateToMillis(today, 7, 15),
                        updatedAt = dateToMillis(today, 7, 15)
                    ),
                    JournalEntry(
                        date = today.minusDays(1),
                        content = "Rainy morning. There's something about the sound of rain that makes journaling feel more natural, like the world is slowing down just for this moment. I've been reading about the science of morning routines and apparently the first hour sets the tone for your entire day. That tracks with my experience. On days when I journal, I notice I'm less reactive in conversations, more patient with my coworkers. Yesterday I almost snapped at someone in a meeting but caught myself. Old me wouldn't have noticed. I want to be more intentional about practicing gratitude. Starting now: I'm grateful for this warm cup of coffee, for a roof over my head, and for the fact that I have people in my life worth being patient for.",
                        wordCount = 140,
                        mood = "\uD83D\uDE4F",
                        createdAt = dateToMillis(today.minusDays(1), 6, 45),
                        updatedAt = dateToMillis(today.minusDays(1), 6, 50)
                    ),
                    JournalEntry(
                        date = today.minusDays(2),
                        content = "Couldn't sleep well last night. Tossed and turned thinking about the project deadline. But you know what, sitting here writing about it makes the anxiety feel more manageable. When worries are just swirling in your head they feel enormous, but on paper they shrink to their actual size. The deadline is Friday. I have three tasks left. Each one takes about two hours. That's six hours of work. I have three days. It's completely doable. Why was I panicking? I think I need to be better about writing things down when I'm stressed instead of letting my brain spiral. Going to make a priority list right after this entry.",
                        wordCount = 120,
                        mood = "\uD83D\uDE1F",
                        createdAt = dateToMillis(today.minusDays(2), 7, 30),
                        updatedAt = dateToMillis(today.minusDays(2), 7, 35)
                    ),
                    JournalEntry(
                        date = today.minusDays(3),
                        content = "Sunday morning and the whole day stretches ahead with nothing planned. I used to fill every minute with scrolling - Instagram, Reddit, Twitter, the endless cycle. Now I actually enjoy having empty space. Took a long walk yesterday evening and noticed a neighbor has started a little herb garden. Made me want to try growing something too. Maybe basil and mint to start, nothing too ambitious. The point is, I'm noticing things now. The world outside my phone is surprisingly interesting when you actually look at it. Planning to cook a proper meal today and maybe call my parents. Simple things that feel meaningful.",
                        wordCount = 109,
                        mood = "\uD83D\uDE42",
                        createdAt = dateToMillis(today.minusDays(3), 8, 20),
                        updatedAt = dateToMillis(today.minusDays(3), 8, 20)
                    ),
                    JournalEntry(
                        date = today.minusDays(4),
                        content = "Had the most productive day at work yesterday and I think it started with this journal. When I sit down and organize my thoughts in the morning, I carry that clarity into everything else. My manager actually commented that my presentation was really well-structured. Little does she know it started with me rambling in a journal app at 6 AM. I've been doing this for over a week now and the streak counter is genuinely motivating. There's something about not wanting to break the chain that keeps me coming back. It's not a chore anymore though. I actually look forward to these quiet minutes.",
                        wordCount = 110,
                        mood = "\uD83E\uDD29",
                        createdAt = dateToMillis(today.minusDays(4), 6, 30),
                        updatedAt = dateToMillis(today.minusDays(4), 6, 35)
                    ),
                    JournalEntry(
                        date = today.minusDays(5),
                        content = "Woke up tired today. Some mornings are harder than others and that's okay. I almost skipped journaling but then I thought about my streak and how the whole point is showing up even when you don't feel like it. So here I am. Not every entry has to be profound or beautiful. Sometimes it's just about putting pen to paper - or fingers to screen in this case. I read somewhere that the habit of writing matters more than what you write. Just the act of reflection changes your brain over time. I believe that. Even this rambling feels better than the alternative of doom-scrolling through bad news.",
                        wordCount = 116,
                        mood = "\uD83D\uDE34",
                        createdAt = dateToMillis(today.minusDays(5), 7, 50),
                        updatedAt = dateToMillis(today.minusDays(5), 7, 55)
                    ),
                    JournalEntry(
                        date = today.minusDays(6),
                        content = "Something funny happened yesterday. A friend asked to see my phone and saw that Instagram was blocked. She was confused and I explained the whole concept - journal before social media. She thought I was crazy at first but by the end of the conversation she was asking me to send her the app link. That felt really good. I'm not usually the person who influences others positively but here I am, accidentally becoming a mindfulness evangelist. Maybe that's what happens when you genuinely find something that works. You can't help but share it. The morning air was crisp today and I watched a bird building a nest outside my window. Five minutes of pure peace.",
                        wordCount = 122,
                        mood = "\uD83D\uDE0A",
                        createdAt = dateToMillis(today.minusDays(6), 7, 0),
                        updatedAt = dateToMillis(today.minusDays(6), 7, 10)
                    ),
                    JournalEntry(
                        date = today.minusDays(7),
                        content = "One week of journaling! I can't believe I've kept this up for seven straight days. Looking back at my first entry, I was skeptical this would stick. But here's what I've noticed: I'm sleeping better, I'm less anxious during the day, and I haven't mindlessly scrolled through social media first thing in the morning in a whole week. That last one is huge for me. I used to lose 30-45 minutes every morning to Instagram and Reddit before even getting out of bed. Now those minutes go to this. To actual thoughts and reflection. I know it's early days but I feel like something is shifting.",
                        wordCount = 115,
                        mood = "\uD83E\uDD29",
                        createdAt = dateToMillis(today.minusDays(7), 6, 55),
                        updatedAt = dateToMillis(today.minusDays(7), 7, 0)
                    ),
                    JournalEntry(
                        date = today.minusDays(8),
                        content = "Tried something new today - I wrote down three things I'm looking forward to this week. Such a simple exercise but it completely reframed my Monday dread into something more positive. I have dinner with an old friend on Wednesday, a half-day Friday, and I'm starting a new book tonight. When I list it out like that, the week actually looks pretty great. I think the trick is that journaling forces you to be specific. Vague anxiety says everything is terrible. Specific writing says actually, here are the good things. Here are the challenges. Here's what I can control. That specificity is powerful.",
                        wordCount = 108,
                        mood = "\uD83D\uDE42",
                        createdAt = dateToMillis(today.minusDays(8), 7, 10),
                        updatedAt = dateToMillis(today.minusDays(8), 7, 15)
                    ),
                    JournalEntry(
                        date = today.minusDays(9),
                        content = "Day one of trying this morning journaling thing. I downloaded this app because I saw it recommended somewhere and honestly I'm skeptical. I've tried journaling apps before and always gave up after a day or two. But the concept of blocking social media until I write something is clever. It's 6:48 AM and I would normally be scrolling through Reddit right now. Instead I'm writing this. Not sure what to write about so I'll just say that it's a Tuesday, the weather is nice, and I made coffee that actually tastes good for once. I got the ratio right. Maybe that's my tiny win for today. Let's see if I come back tomorrow.",
                        wordCount = 120,
                        mood = "\uD83D\uDE10",
                        createdAt = dateToMillis(today.minusDays(9), 6, 48),
                        updatedAt = dateToMillis(today.minusDays(9), 6, 55)
                    )
                )

                for (entry in entries) {
                    repo.insert(entry)
                }

                Log.d("SeedData", "Successfully seeded ${entries.size} journal entries!")
            } catch (e: Exception) {
                Log.e("SeedData", "Failed to seed data", e)
            }
        }
    }

    private fun dateToMillis(date: LocalDate, hour: Int, minute: Int): Long {
        return date.atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
