# Architecture

## Layers

- **UI Layer**: Jetpack Compose + ViewModel.
  - **UI State Ownership**: ViewModels own the UI state using `StateFlow`.
  - **UI State**: Immutable data classes implementing `UiState`.
  - **UI Events**: Actions from UI to ViewModel implementing `UiEvent`.
  - **UI Effects**: One-off events (navigation, toast) implementing `UiEffect`.
- **Domain Layer**: Business logic.
  - **UseCases**: (Optional for Phase 1, if logic is simple it can stay in ViewModel/Repository).
  - **Repositories (Interfaces)**: Defined here and kept minimal until each feature step needs more detail.
  - **Models**: Plain data classes shared across UI and Data boundaries.
- **Data Layer**: Data sources.
  - **Repositories (Implementations)**: Implement domain interfaces.
  - **DataSources**: DB (Room), Preferences (DataStore / EncryptedSharedPreferences), Network (google-genai).
  - **Current Step 4 Scope**: Interfaces and package boundaries are prepared first; concrete implementations arrive in Steps 5-9.

## Dependency Injection

- Powered by **Hilt**.
- `AppModule` owns shared coroutine dispatchers only.
- `DatabaseModule`, `PreferencesModule`, `NetworkModule`, and `RepositoryModule` own feature-specific bindings as they are introduced.
- ViewModels own screen state and depend on repositories rather than concrete data sources.
