package io.github.ikinocore.gemread.android.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream

class SeedTemplatesUseCaseTest {

    private val context: Context = mockk()
    private val repository: PromptTemplateRepository = mockk()
    private val useCase = SeedTemplatesUseCase(context, repository)

    @Test
    fun `invoke should not seed when repository is not empty`() = runTest {
        coEvery { repository.getCount() } returns 5

        useCase()

        coVerify(exactly = 0) { repository.seedTemplates(any()) }
    }

    @Test
    fun `invoke should seed when repository is empty`() = runTest {
        coEvery { repository.getCount() } returns 0

        val assets: AssetManager = mockk()
        every { context.assets } returns assets
        val json = """
            [
              {
                "title": "Translate",
                "systemPrompt": "Translate to Japanese",
                "sortOrder": 1,
                "isDefault": true
              }
            ]
        """.trimIndent()
        every { assets.open("prompt_templates_ja.json") } returns ByteArrayInputStream(json.toByteArray())
        coEvery { repository.seedTemplates(any()) } returns Unit

        useCase()

        coVerify(exactly = 1) { repository.seedTemplates(any()) }
    }
}
