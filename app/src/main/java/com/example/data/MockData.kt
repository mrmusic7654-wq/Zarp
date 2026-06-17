package com.example.data

import com.example.model.Conversation
import com.example.model.Message
import java.util.UUID

object MockData {
    val conversations = listOf(
        Conversation(
            id = "1",
            title = "Trip planning for Paris",
            dateGroup = "Today",
            messages = listOf(
                Message("m1", "Can you help me plan a 3-day trip to Paris?", true, System.currentTimeMillis() - 1000 * 60 * 60),
                Message("m2", "Absolutely! Here is a suggested 3-day itinerary for Paris:\n\n**Day 1: Classic Paris**\n• Morning: Eiffel Tower\n• Afternoon: Louvre Museum\n• Evening: Seine River Cruise\n\n**Day 2: Art and History**\n• Morning: Notre-Dame\n• Afternoon: Musée d'Orsay\n• Evening: Montmartre\n\n**Day 3: Versailles**\n• Full Day: Palace of Versailles", false, System.currentTimeMillis() - 1000 * 60 * 59)
            )
        ),
        Conversation(
            id = "2",
            title = "Help with resume",
            dateGroup = "Today",
            messages = listOf(
                Message("m3", "I need to update my resume for an Android developer role.", true, System.currentTimeMillis() - 1000 * 60 * 60 * 5),
                Message("m4", "Great! What key skills do you want to highlight? For Android roles, you should typically include:\n\n1. Kotlin / Java\n2. Jetpack Compose\n3. MVVM Architecture\n4. Coroutines & Flow", false, System.currentTimeMillis() - 1000 * 60 * 60 * 4)
            )
        ),
        Conversation(
            id = "3",
            title = "Recipe ideas for dinner",
            dateGroup = "Yesterday",
            messages = listOf(
                Message("m5", "I have some chicken, rice, and broccoli. What can I make?", true, System.currentTimeMillis() - 1000 * 60 * 60 * 25),
                Message("m6", "You can make a delicious **Chicken and Broccoli Stir-fry**. Here's a simple recipe:\n\n• Sauté chicken in a pan.\n• Steam the broccoli.\n• Mix some soy sauce, garlic, and ginger for a simple sauce.\nServe over the rice!", false, System.currentTimeMillis() - 1000 * 60 * 60 * 24)
            )
        ),
        Conversation(
            id = "4",
            title = "Explain Compose",
            dateGroup = "Yesterday",
            messages = listOf(
                Message("m7", "Can you explain Jetpack Compose code with an example?", true, System.currentTimeMillis() - 1000 * 60 * 60 * 40),
                Message("m8", "Sure! Jetpack Compose is a modern UI toolkit. Use the `@Composable` annotation.\n\nHere is a simple example:\n\n```kotlin\n@Composable\nfun Greeting(name: String) {\n    Text(text = \"Hello \\${'$'}name!\")\n}\n```\n\nYou can also use inline code like `Modifier.padding(16.dp)` to style elements.", false, System.currentTimeMillis() - 1000 * 60 * 60 * 39)
            )
        ),
        Conversation(
            id = "5",
            title = "Book recommendations",
            dateGroup = "Previous 7 days",
            messages = listOf(
                Message("m9", "Recommend me some sci-fi books.", true, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 4),
                Message("m10", "Here are some top sci-fi books:\n\n1. Dune by Frank Herbert\n2. Neuromancer by William Gibson\n3. The Foundation series by Isaac Asimov", false, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 4 + 1000 * 60)
            )
        ),
        Conversation(
            id = "6",
            title = "Workout routine",
            dateGroup = "Previous 7 days",
            messages = listOf(
                Message("m11", "I need a quick 15-minute home workout.", true, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 5),
                Message("m12", "Here is a 15-minute high-intensity bodyweight routine:\n\n• 3 minutes: Jumping jacks (warmup)\n• 4 minutes: Push-ups\n• 4 minutes: Squats\n• 4 minutes: Plank variations", false, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 5 + 1000 * 60)
            )
        ),
        Conversation(
            id = "7",
            title = "Debugging null pointer",
            dateGroup = "Older",
            messages = listOf(
                Message("m13", "I'm getting a NullPointerException when accessing a text view in my fragment.", true, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 15),
                Message("m14", "A `NullPointerException` occurs when you call a method or access a field on a null object. In fragments, ensure you find the view inside `onViewCreated()` or use View Binding safe calls like `binding?.myTextView?.text = \"...\"`.", false, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 15 + 1000 * 60)
            )
        ),
        Conversation(
            id = "8",
            title = "Meditation strategies",
            dateGroup = "Older",
            messages = listOf(
                Message("m15", "How do I start meditating?", true, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 30),
                Message("m16", "Start with just 5 minutes a day. Focus on your breath. When your mind wanders, gently bring it back. You can try guided apps or simple silence.", false, System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 30 + 1000 * 60)
            )
        )
    )
}
