package com.geoquiz.app.ui.silhouettes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * A small nit from the task 5.1 review: the setup screen's section headers have an "Expand this
 * section" click label at rest, and tapping one reveals its options.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class SilhouettesSetupScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun viewModel(): SilhouettesSetupViewModel {
        val countryRepository = mockk<CountryRepository>()
        every { countryRepository.getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        val mode = SilhouettesGameMode(
            CountriesQuestionGenerator(mockk<GetCountriesForQuizUseCase>(relaxed = true), mockk<GetCountriesForFlagQuizUseCase>(relaxed = true)),
            mockk<ValidateAnswerUseCase>(),
            CalculateScoreUseCase(),
            QuizRandom(Random(1))
        )
        return SilhouettesSetupViewModel(
            countryRepository = countryRepository,
            optionsBuilder = CategoryOptionsFixture.builder(),
            silhouettesGameMode = mode
        )
    }

    private fun launch() {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                SilhouettesSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel())
            }
        }
        compose.waitForIdle()
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClick label = $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun `a collapsed section's click label is Expand this section, and tapping it reveals its options`() {
        launch()

        val list = compose.onNode(hasScrollAction())

        // "All countries" starts expanded; "By region" is the next section, collapsed at rest.
        list.performScrollToNode(hasText("By region"))
        val byRegionHeader = compose.onNode(hasText("By region").and(hasClickLabel("Expand this section")))
        byRegionHeader.assertIsDisplayed()

        byRegionHeader.performClick()
        compose.waitForIdle()

        compose.onNode(hasText("By region").and(hasClickLabel("Collapse this section"))).assertIsDisplayed()
        list.performScrollToNode(hasText("Europe"))
        compose.onNodeWithText("Europe").assertIsDisplayed()
    }
}
