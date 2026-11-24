import { Routes } from '@angular/router';
import {
  administradorGuard,
  autenticadoGuard,
  conexoesDeOutraPessoaGuard,
  verificacaoPendenteGuard,
  visitanteGuard,
} from './auth/auth.guards';
import { Cadastro } from './auth/cadastro/cadastro';
import { Entrar } from './auth/entrar/entrar';
import { RedefinirSenha } from './auth/redefinir-senha/redefinir-senha';
import { VerificarEmail } from './auth/verificar-email/verificar-email';
import { BolhaForm } from './bolhas/bolha-form/bolha-form';
import { BolhaPagina } from './bolhas/bolha-pagina/bolha-pagina';
import { Explorar } from './bolhas/explorar/explorar';
import { BuscaPagina } from './busca/busca-pagina/busca-pagina';
import { ConexoesPagina } from './conexoes/conexoes-pagina/conexoes-pagina';
import { Inicio } from './inicio/inicio';
import { Shell } from './layout/shell/shell';
import { AnuncioForm } from './marketplace/anuncio-form/anuncio-form';
import { AnuncioPagina } from './marketplace/anuncio-pagina/anuncio-pagina';
import { MarketplacePagina } from './marketplace/marketplace-pagina/marketplace-pagina';
import { ConversaAberta } from './mensagens/conversa-aberta/conversa-aberta';
import { MensagensPagina, SemConversa } from './mensagens/mensagens-pagina/mensagens-pagina';
import { NotificacoesPagina } from './notificacoes/notificacoes-pagina/notificacoes-pagina';
import { AlterarSenha } from './perfil/alterar-senha/alterar-senha';
import { EditarPerfil } from './perfil/editar-perfil/editar-perfil';
import { PerfilPagina } from './perfil/perfil-pagina/perfil-pagina';
import { PublicacaoPagina } from './publicacoes/publicacao-pagina/publicacao-pagina';
import { SalvosPagina } from './publicacoes/salvos-pagina/salvos-pagina';

export const routes: Routes = [
  { path: 'entrar', component: Entrar, canActivate: [visitanteGuard], title: 'Entrar · CampusConnect' },
  { path: 'cadastro', component: Cadastro, canActivate: [visitanteGuard], title: 'Criar conta · CampusConnect' },
  {
    path: 'verificar-email',
    component: VerificarEmail,
    canActivate: [visitanteGuard, verificacaoPendenteGuard],
    title: 'Confirmar e-mail · CampusConnect',
  },
  {
    // O "Esqueceu a senha?" do login. Os três passos (e-mail, código e senha nova) ficam na mesma tela.
    path: 'redefinir-senha',
    component: RedefinirSenha,
    canActivate: [visitanteGuard],
    title: 'Redefinir senha · CampusConnect',
  },
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
      {
        // A troca de senha de quem está logado. O link fica no fim de "Editar perfil".
        path: 'perfil/editar/senha',
        component: AlterarSenha,
        title: 'Alterar senha · CampusConnect',
        data: { trilha: ['Perfil', 'Alterar senha'] },
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
        // O Enter no campo de busca da barra do topo leva para cá, com o termo em `q` e a aba em `aba`.
        path: 'busca',
        component: BuscaPagina,
        title: 'Busca · CampusConnect',
        data: { trilha: ['Busca'] },
      },
      {
        // O sino da barra do topo e o item "Notificações" do menu levam para cá.
        path: 'notificacoes',
        component: NotificacoesPagina,
        title: 'Notificações · CampusConnect',
        data: { trilha: ['Notificações'] },
      },
      {
        path: 'marketplace',
        component: MarketplacePagina,
        title: 'Marketplace · CampusConnect',
        data: { trilha: ['Marketplace'] },
      },
      {
        // Antes de ':id': "novo" não é um id de anúncio.
        path: 'marketplace/novo',
        component: AnuncioForm,
        title: 'Anunciar · CampusConnect',
        data: { trilha: ['Marketplace', 'Anunciar'] },
      },
      {
        // Só quem anunciou edita; a página volta para o anúncio se for outra pessoa.
        path: 'marketplace/:id/editar',
        component: AnuncioForm,
        title: 'Editar anúncio · CampusConnect',
        data: { trilha: ['Marketplace', 'Editar anúncio'] },
      },
      {
        // A página troca a trilha pelo título do anúncio depois de carregar.
        path: 'marketplace/:id',
        component: AnuncioPagina,
        title: 'Anúncio · CampusConnect',
        data: { trilha: ['Marketplace'] },
      },
      {
        // A lista de conversas fica na tela; só a conversa aberta, à direita, troca.
        path: 'mensagens',
        component: MensagensPagina,
        children: [
          { path: '', component: SemConversa, title: 'Mensagens · CampusConnect', data: { trilha: ['Mensagens'] } },
          {
            // A página troca a trilha pelo nome da outra pessoa depois de carregar.
            path: ':id',
            component: ConversaAberta,
            title: 'Mensagens · CampusConnect',
            data: { trilha: ['Mensagens'] },
          },
        ],
      },
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
