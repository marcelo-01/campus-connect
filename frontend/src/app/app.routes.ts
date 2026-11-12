import { Routes } from '@angular/router';
import {
  administradorGuard,
  autenticadoGuard,
  conexoesDeOutraPessoaGuard,
  visitanteGuard,
} from './auth/auth.guards';
import { Cadastro } from './auth/cadastro/cadastro';
import { Entrar } from './auth/entrar/entrar';
import { BolhaForm } from './bolhas/bolha-form/bolha-form';
import { BolhaPagina } from './bolhas/bolha-pagina/bolha-pagina';
import { Explorar } from './bolhas/explorar/explorar';
import { ConexoesPagina } from './conexoes/conexoes-pagina/conexoes-pagina';
import { Inicio } from './inicio/inicio';
import { Shell } from './layout/shell/shell';
import { EditarPerfil } from './perfil/editar-perfil/editar-perfil';
import { PerfilPagina } from './perfil/perfil-pagina/perfil-pagina';
import { PublicacaoPagina } from './publicacoes/publicacao-pagina/publicacao-pagina';
import { SalvosPagina } from './publicacoes/salvos-pagina/salvos-pagina';

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
      {
        // As conexões de quem está logado, com os pedidos. O item "Conexões" do menu leva para cá.
        path: 'conexoes',
        component: ConexoesPagina,
        title: 'Conexões · CampusConnect',
        data: { trilha: ['Conexões'] },
      },
      {
        // As de outra pessoa. As da própria vão para '/conexoes', onde também ficam os pedidos.
        path: 'perfil/:username/conexoes',
        component: ConexoesPagina,
        canActivate: [conexoesDeOutraPessoaGuard],
        title: 'Conexões · CampusConnect',
        data: { trilha: ['Perfil', 'Conexões'] },
      },
      {
        // A publicação com os comentários. O botão "Compartilhar" do card copia este endereço.
        path: 'publicacoes/:id',
        component: PublicacaoPagina,
        title: 'Publicação · CampusConnect',
        data: { trilha: ['Publicação'] },
      },
      { path: 'salvos', component: SalvosPagina, title: 'Salvos · CampusConnect', data: { trilha: ['Salvos'] } },
      {
        path: 'bolhas',
        component: Explorar,
        title: 'Explorar bolhas · CampusConnect',
        data: { trilha: ['Explorar bolhas'] },
      },
      {
        // Antes de ':slug': "nova" é um slug reservado no backend.
        path: 'bolhas/nova',
        component: BolhaForm,
        canActivate: [administradorGuard],
        title: 'Nova bolha · CampusConnect',
        data: { trilha: ['Explorar bolhas', 'Nova bolha'] },
      },
      {
        path: 'bolhas/:slug/editar',
        component: BolhaForm,
        canActivate: [administradorGuard],
        title: 'Editar bolha · CampusConnect',
        data: { trilha: ['Explorar bolhas', 'Editar bolha'] },
      },
      {
        // A página troca a trilha pela hierarquia da bolha depois de carregar.
        path: 'bolhas/:slug',
        component: BolhaPagina,
        title: 'Bolha · CampusConnect',
        data: { trilha: ['Explorar bolhas'] },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
