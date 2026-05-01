package io.github.ikinocore.gemread.android.domain.usecase

import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verification 2: API キー未設定判定の単体テスト。
 * SecurePreferences をモック化し、API キーの有無で判定結果が変わることを確認する。
 */
class IsSettingsCompletedUseCaseTest {

    private lateinit var securePreferences: SecurePreferences
    private lateinit var useCase: IsSettingsCompletedUseCase

    @Before
    fun setUp() {
        securePreferences = mockk()
        useCase = IsSettingsCompletedUseCase(securePreferences)
    }

    @Test
    fun `returns false when api key is null`() {
        // API キーが null の場合は設定未完了と判定する
        every { securePreferences.getApiKey() } returns null

        assertFalse(useCase())
    }

    @Test
    fun `returns false when api key is blank`() {
        // API キーが空文字の場合も設定未完了と判定する
        every { securePreferences.getApiKey() } returns ""

        assertFalse(useCase())
    }

    @Test
    fun `returns false when api key is whitespace only`() {
        // 空白のみのキーも無効と判定する
        every { securePreferences.getApiKey() } returns "   "

        assertFalse(useCase())
    }

    @Test
    fun `returns true when api key is set`() {
        // 有効な API キーが設定済みの場合は設定完了と判定する
        every { securePreferences.getApiKey() } returns "AIzaSy_valid_api_key"

        assertTrue(useCase())
    }
}
