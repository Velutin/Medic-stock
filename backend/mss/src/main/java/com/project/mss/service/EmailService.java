package com.project.mss.service;

import org.springframework.stereotype.Service;

import com.project.mss.dto.email.EmailDTO;
import com.project.mss.producer.EmailProducer;

@Service
public class EmailService {

    private final EmailProducer emailProducer;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(EmailProducer emailProducer) {
        this.emailProducer = emailProducer;
    }

    /** First-access invitation: the link opens the page where the user creates the password. */
    public void sendInvitationEmail(Long userId, String email, String name, String token) {
        String link = frontendUrl + "/primeiro-acesso?token=" + token;
        emailProducer.publishEmailMessage(new EmailDTO(
                userId,
                email,
                "Acesso ao sistema de estoque",
                String.format("""
                Olá, %s!

                Você foi cadastrado(a) no sistema de estoque.

                Para criar sua senha e fazer o primeiro acesso, use o link abaixo:
                %s

                Este link é pessoal, pode ser usado uma única vez e expira em 48 horas.
                Se ele expirar, peça ao administrador para reenviar o convite.

                Atenciosamente,
                Equipe MSS
                """, name, link)));
    }

    public void sendPasswordChangeConfirmationEmail(Long userId, String email) {
        emailProducer.publishEmailMessage(new EmailDTO(
                userId,
                email,
                "Confirmação de alteração de senha",
                """
                Olá,

                Sua senha foi alterada com sucesso. As sessões abertas em outros aparelhos foram encerradas.

                Se você não realizou esta alteração, entre em contato com o administrador imediatamente.

                Atenciosamente,
                Equipe MSS
                """));
    }

    public void sendPasswordResetEmail(Long userId, String email, String name, String token) {
        String link = frontendUrl + "/redefinir-senha?token=" + token;
        emailProducer.publishEmailMessage(new EmailDTO(
                userId,
                email,
                "Recuperação de senha",
                String.format("""
                Olá, %s!

                Você solicitou a recuperação de senha da sua conta no sistema de estoque.

                Clique no link abaixo para criar uma nova senha:
                %s

                Este link expira em 30 minutos.

                Se você não solicitou esta recuperação, ignore este e-mail.

                Atenciosamente,
                Equipe MSS
                """, name, link)));
    }
}
