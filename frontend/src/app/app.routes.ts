import { Routes } from '@angular/router';
import { autenticadoGuard, visitanteGuard } from './auth/auth.guards';
import { Cadastro } from './auth/cadastro/cadastro';
import { Entrar } from './auth/entrar/entrar';
import { Inicio } from './inicio/inicio';

export const routes: Routes = [
  { path: 'entrar', component: Entrar, canActivate: [visitanteGuard], title: 'Entrar · CampusConnect' },
  { path: 'cadastro', component: Cadastro, canActivate: [visitanteGuard], title: 'Criar conta · CampusConnect' },
  { path: '', component: Inicio, canActivate: [autenticadoGuard], title: 'CampusConnect' },
  { path: '**', redirectTo: '' },
];
