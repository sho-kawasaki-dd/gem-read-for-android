package io.github.ikinocore.gemread.android.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Verification 3: テンプレート seed の単体テスト。
 * 初回起動時のみ seed が実行され、再起動時（DB に件数あり）は重複投入されないことを確認する。
 */
class SeedTemplatesUseCaseTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var repository: PromptTemplateRepository
    private lateinit var useCase: SeedTemplatesUseCase

    @Before
    fun setUp() {
        context = mockk()
        assetManager = mockk()
        repository = mockk()
        every { context.assets } returns assetManager
        useCase = SeedTemplatesUseCase(context, repository)
    }

    @Test
    fun `does not seed when templates already exist`() = runTest {
        // DB にテンプレートが 1 件以上あれば seed を実行しない（重複投入防止）
        coEvery { repository.getCount() } returns 4

        useCase()

        coVerify(exactly = 0) { repository.seedTemplates(any()) }
    }

    @Test
    fun `seeds templates from assets when database is empty`() = runTest {
        // DB が空の場合、assets JSON を読み込んで seed を 1 度だけ実行する
        val sampleJson = """
            [
              {"title":"要約","systemPrompt":"要約してください","sortOrder":1,"isDefault":true},
              {"title":"翻訳","systemPrompt":"翻訳してください","sortOrder":2,"isDefault":false},
              {"title":"解説","systemPrompt":"解説してください","sortOrder":3,"isDefault":false},
              {"title":"校正","systemPrompt":"校正してください","sortOrder":4,"isDefault":false}
            ]
        """.trimIndent()
        every { assetManager.open("prompt_templates_ja.json") } returns sampleJson.byteInputStream()
        coEvery { repository.getCount() } returns 0
        coEvery { repository.seedTemplates(any()) } just Runs

        useCase()

        // seedTemplates が正確に 1 回呼ばれることを確認する
        coVerify(exactly = 1) { repository.seedTemplates(any()) }
    }

    @Test
    fun `seeds exactly 4 templates from assets`() = runTest {
        // assets JSON から 4 件のテンプレートが投入されることを確認する
        val sampleJson = """
            [
              {"title":"要約","systemPrompt":"要約してください","sortOrder":1,"isDefault":true},
              {"title":"翻訳","systemPrompt":"翻訳してください","sortOrder":2,"isDefault":false},
              {"title":"解説","systemPrompt":"解説してください","sortOrder":3,"isDefault":false},
              {"title":"校正","systemPrompt":"校正してください","sortOrder":4,"isDefault":false}
            ]
        """.trimIndent()
        every { assetManager.open("prompt_templates_ja.json") } returns sampleJson.byteInputStream()
        coEvery { repository.getCount() } returns 0
        coEvery { repository.seedTemplates(any()) } just Runs

        useCase()

        coVerify(exactly = 1) {
            repository.seedTemplates(match { it.size == 4 })
        }
    }
}
