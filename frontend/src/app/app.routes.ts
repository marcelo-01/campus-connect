import { Routes } from '@angular/router';
import { autenticadoGuard, visitanteGuard } from './auth/auth.guards';
import { Cadastro } from './auth/cadastro/cadastro';
import { Entrar } from './auth/entrar/entrar';
import { Inicio } from './inicio/inicio';
import { Shell } from './layout/shell/shell';
import { EditarPerfil } from './perfil/editar-perfil/editar-perfil';
import { PerfilPagina } from './perfil/perfil-pagina/perfil-pagina';

export const routes: Routes = [
  { path: 'entrar', component: Entrar, canActivate: [visitanteGuard], title: 'Entrar · CampusConnect' },
  { path: 'cadastro', component: Cadastro, canActivate: [visitanteGuard], title: 'Criar conta · CampusConnect' },
  {
    // Telas logadas, dentro do layout do MVP. `data.trilha` alimenta a barra do topo.
    path: '',
    component: Shell,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', component: Inicio, title: 'CampusConnect', data: { trilha: ['Início'] } },
      { path: 'perfil', component: PerfilPagina, title: 'Perfil · CampusConnect', data: { trilha: ['Perfil'] } },
      {
        // Antes de ':username': "editar" é um username reservado no backend.
        path: 'perfil/editar',
        component: EditarPerfil,
        title: 'Editar perfil · CampusConnect',
        data: { trilha: ['Perfil', 'Editar perfil'] },
      },
      { path: 'perfil/:username', component: PerfilPagina, title: 'Perfil · CampusConnect', data: { trilha: ['Perfil'] } },
    ],
  },
  { path: '**', redirectTo: '' },
];
