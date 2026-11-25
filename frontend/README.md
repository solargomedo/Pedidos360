# Frontend Angular · Pedidos360

Angular 21.2.x, RxJS y MSAL. Entra ID emite el access token para el scope `access_as_user`. El interceptor MSAL agrega el Bearer JWT y el BFF aplica permisos por rol.

Desde frontend, usar npm ci y npm start. El modo predeterminado y producción usan API Gateway. Para probar Pedidos con BFF en localhost:8080, usar `npm start -- --configuration local --host localhost`. Ese modo cambia solo la URL de Pedidos. Catálogo conserva Gateway. Ejecutar `npm test -- --watch=false` y npm run build para validar.

Admin y Operador ven pedidos procesados por Kafka, unidades y conteo por estado. El polling consulta al abrir la vista y luego cada cinco segundos. timer genera el intervalo, exhaustMap evita solapamiento, catchError conserva el último resultado y takeUntilDestroyed cancela al salir. Cliente puede crear pedidos y carece del permiso de lectura de analítica. El seguimiento individual se demuestra por API.

Ver [demostración](../DEMO-EP3.md). Las referencias generales de Angular CLI se conservan a continuación.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.24.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
